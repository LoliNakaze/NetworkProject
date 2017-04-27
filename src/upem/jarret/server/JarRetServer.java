package upem.jarret.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import upem.jarret.http.HTTPException;
import upem.jarret.http.HTTPHeader;
import upem.jarret.http.HTTPReader;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * Created by nakaze on 15/04/17.
 */
public class JarRetServer {
    private enum Command {
        SHUTDOWN_NOW, SHUTDOWN, INFO
    }

    private static final Charset CHARSET_ASCII = Charset.forName("ASCII");

    private enum State {
        CONNECTION, TASK, RESPONSE, END
    }

    private enum ReadState {
        HEADER, CONTENT
    }

    static class Context {
        private final ByteBuffer buffer = ByteBuffer.allocate(BUF_SIZE);
        private final SelectionKey key;
        private final SocketChannel sc;
        private JobMonitor jobMonitor = null;

        private ReadState readState;
        private State state;

        private byte[] last = new byte[4];
        private HTTPHeader header;
        private HTTPReader reader;

        public Context(SelectionKey key) {
            this.key = key;
            this.sc = (SocketChannel) key.channel();
            state = State.CONNECTION;
            readState = ReadState.HEADER;
            reader = HTTPReader.useStringReader(buffer);
        }

        private void bufferShift() {
            last[0] = last[1];
            last[1] = last[2];
            last[2] = last[3];
        }

        private void clearBuffer() {
            last[0] = last[1] = last[2] = last[3] = 0;
        }

        private boolean isEndHeader() {
            if (last[0] == '\r' && last[1] == '\n' && last[2] == '\r' && last[3] == '\n')
                return true;
            return false;
        }

        void doRead() throws IOException {
            int read;
            int position = buffer.position();

            if ((read = sc.read(buffer)) == -1 || read == 0)
                return;

//            System.out.println(state + " " + readState);
            switch (readState) {
                case HEADER:
                    ByteBuffer tmp = buffer.duplicate();
                    tmp.position(position);

                    while (buffer.hasRemaining()) {
                        bufferShift();
                        last[3] = tmp.get();
                        if (isEndHeader()) {
                            header = reader.readHeader();

                            if (analyzeIf(-1 == header.getContentLength()))
                                break;

                            if (analyzeIf(buffer.position() >= header.getContentLength())) {
                                readState = ReadState.CONTENT;
                                break;
                            }

                            buffer.limit(buffer.capacity());
                        }
                    }

                    break;
                case CONTENT:
                    analyzeIf(buffer.position() >= header.getContentLength());
                    header = null;
                    break;
            }
        }

        private boolean analyzeIf(boolean b) throws IOException {
            if (b) {
                analyzeAnswer();
                clearBuffer();
            }

            return b;
        }

        void doWrite() throws IOException {
            buffer.flip();

            if (sc.write(buffer) == 0) {
                buffer.compact();
                return;
            }

            buffer.compact();

            if (buffer.position() == 0) {
                buffer.clear();
                switch (state) {
                    case TASK:
                        state = State.RESPONSE;
                        break;
                    case END:
                        state = State.CONNECTION;
                        break;
                    default:
                        throw new IllegalStateException("Impossible state in write mode: " + state.toString());
                }

                readState = ReadState.HEADER;
                key.interestOps(SelectionKey.OP_READ);
            }
        }

        private void analyzeAnswer() throws IOException {
//            printBuffer(buffer);

            switch (state) {
                case CONNECTION:
                    buffer.clear();
                    String[] split = header.getResponse().split(" ");
                    if (!(split[0].equals("GET") && split[1].equals("Task"))) {
                        buffer.put(CHARSET_ASCII.encode(badRequest()));
                        state = State.END;
                    } else {
                        if (!jobList.stream().filter(j -> !(j.isComplete())).findAny().isPresent()) {
                            buffer.put(CHARSET_ASCII.encode(comeback()));
                            System.out.println("Comeback");
                            state = State.END;
                            break;
                        }
                        Charset contentCharset = (header.getCharset() != null) ? header.getCharset() : Charset.forName("UTF-8");

                        jobMonitor = randPriorityMonitor();

                        String task = jobMonitor.sendTask();
                        buffer.put(CHARSET_ASCII.encode(ok()));
                        buffer.put(CHARSET_ASCII.encode("Content-Type: application/json; charset=utf-8\r\n"
                                + "Content-Length: " + task.length() + "\r\n"
                                + "\r\n"));
                        buffer.put(contentCharset.encode(task));

                        state = State.TASK;
                    }
                    break;
                case RESPONSE:
                    ObjectMapper mapper = new ObjectMapper();
                    ByteBuffer tmp = reader.readBytes(header.getContentLength());
                    buffer.clear();
                    tmp.flip();
                    buffer.put(tmp);
                    buffer.flip();
                    long jobId = buffer.getLong();
                    int taskId = buffer.getInt();

                    Charset charset = header.getCharset();

                    if (charset == null)
                        charset = Charset.forName("UTF-8");

                    String bodyJson = charset.decode(buffer).toString();

                    HashMap<String, Object> map = mapper.readValue(bodyJson, HashMap.class);

                    buffer.clear();
                    Object answer = map.get("Answer");

                    if (answer == null) {
                        Object error = map.get("Error");

                        if (error == null) {
                            buffer.put(CHARSET_ASCII.encode(badRequest()));
                        } else {
                            // TODO : A demander : est-ce qu'une erreur dans une task fait que la task est exécutée ?
                            jobMonitor.updateATask(Integer.parseInt((String) map.get("Task")), error.toString());
                            buffer.put(CHARSET_ASCII.encode(ok()));
                        }
                    } else {
                        jobMonitor.updateATask(Integer.parseInt((String) map.get("Task")), answer.toString());
                        buffer.put(CHARSET_ASCII.encode(ok()));
                    }
                    state = State.END;
                    break;
            }

            key.interestOps(SelectionKey.OP_WRITE);
        }

        private void printBuffer(ByteBuffer buffer) {
            ByteBuffer duplicate = buffer.duplicate();
            duplicate.flip();
            System.out.println(CHARSET_ASCII.decode(duplicate).toString());
        }

        private String badRequest() {
            return "HTTP/1.1 400 Bad Request\r\n";
        }

        private String ok() {
            return "HTTP/1.1 200 OK\r\n";
        }

        private String comeback() {
            String body = "{"
                    + "\"ComeBackInSeconds\" : " + configuration.comeback
                    + "}";
            return ok()
                    + "Content-Type: application/json; charset=utf-8\r\n"
                    + "Content-Length: " + body.length() + "\r\n"
                    + "\r\n"
                    + body;
        }

        private JobMonitor randPriorityMonitor() {
            int randInt = rand.nextInt() % JobMonitor.getPrioritySum();

            for (int i = 0, j = 0; i < jobList.size(); i++) {
                j += jobList.get(i).getJobPriority();
                if (j >= randInt)
                    return jobList.get(i);
            }

            throw new IllegalStateException("Impossible state normally");
        }
    }

    static class Configuration {
        int port;
        String logPath;
        String answerPath;
        int maxSize;
        int comeback;

        Configuration(int port, String logPath, String answerPath, int maxSize, int comeback) {
            this.port = port;
            this.logPath = logPath;
            this.answerPath = answerPath;
            this.maxSize = maxSize;
            this.comeback = comeback;
        }

        static Configuration fromFile(Path path) throws IOException {
            ObjectMapper mapper = new ObjectMapper();

            try (InputStream in = Files.newInputStream(path, StandardOpenOption.READ)) {
                Map<String, Object> map = mapper.readValue(in, HashMap.class);

                int port = (map.get("port") == null) ? 7777 : Integer.parseInt((String) map.get("port"));
                String logPath = (map.get("logPath") == null) ? "log" : (String) map.get("logPath");
                String answerPath = (map.get("answerPath") == null) ? "answer" : (String) map.get("answerPath");
                int maxSize = (map.get("maxSize") == null) ? Integer.MAX_VALUE : Integer.parseInt((String) map.get("maxSize"));
                int comeback = (map.get("comeback") == null) ? 300 : Integer.parseInt((String) map.get("comeback"));

                return new Configuration(port, logPath, answerPath, maxSize, comeback);
            }
        }

        static Configuration defaultConfiguration() {
            return new Configuration(7777, "log/", "answer/", Integer.MAX_VALUE, 300);
        }
    }

    private static final int BUF_SIZE = 4096;
    private final ServerSocketChannel serverSocketChannel;
    private final Selector selector;
    private final Set<SelectionKey> selectedKeys;
    private static List<JobMonitor> jobList;

    private static final Random rand = new Random();
    private final Thread listener = new Thread(() -> startCommandListener(System.in));
    private static Configuration configuration;

    private final ArrayBlockingQueue<Command> commandQueue = new ArrayBlockingQueue<>(5);
    private Map<Command, Runnable> commandMap = new EnumMap<>(Command.class);

    public JarRetServer(Path jobPath, Path configPath) throws IOException {
        configuration = (configPath != null) ? Configuration.fromFile(configPath) : Configuration.defaultConfiguration();

        serverSocketChannel = ServerSocketChannel.open().bind(new InetSocketAddress(configuration.port));
        selector = Selector.open();
        selectedKeys = selector.selectedKeys();

        jobList = JobMonitor.jobMonitorListFromFile(jobPath, configuration.answerPath);

        commandMap.put(Command.SHUTDOWN, () -> silentlyClose(serverSocketChannel));
        commandMap.put(Command.SHUTDOWN_NOW, () -> {
            listener.interrupt();
            closeAllMonitors();
            Thread.currentThread().interrupt();
        });
        commandMap.put(Command.INFO, this::printKeys);
    }

    /**
     * Launches the server.
     *
     * @throws IOException
     */
    public void launch() throws IOException {
        listener.start();

        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        Set<SelectionKey> selectedKeys = selector.selectedKeys();
        while (!Thread.interrupted()) {
            selector.select();

            Command command = commandQueue.poll();
            if (command != null) commandMap.get(command).run();

            processSelectedKeys();
            selectedKeys.clear();
        }
    }

    private void startCommandListener(InputStream in) {
         try(Scanner scanner = new Scanner(in)) {
            while (!Thread.interrupted() && scanner.hasNextLine()) {
                String line = scanner.nextLine();

                switch (line) {
                    case "SHUTDOWN_NOW":
                    case "INFO":
                    case "SHUTDOWN":
                        commandQueue.put(Command.valueOf(line));
                        selector.wakeup();
                        break;
                    default:
                        System.err.println("Unknown command.");
                        break;
                }
            }
        } catch (InterruptedException e) {
        	return;
        }
    }

    private void processSelectedKeys() throws IOException {
        for (SelectionKey key : selectedKeys) {
            if (key.isValid() && key.isAcceptable()) {
                doAccept(key);
            }
            try {
                Context cntxt = (Context) key.attachment();
                if (key.isValid() && key.isWritable()) {
                    cntxt.doWrite();
                }
                if (key.isValid() && key.isReadable()) {
                    cntxt.doRead();
                }
            } catch (IOException e) {
                e.printStackTrace();
                silentlyClose(key.channel());
            }
        }
    }

    private void doAccept(SelectionKey key) throws IOException {
        SocketChannel sc = serverSocketChannel.accept();
        sc.configureBlocking(false);
        SelectionKey clientKey = sc.register(selector, SelectionKey.OP_READ);
        clientKey.attach(new Context(clientKey));
    }

    private void closeAllMonitors() {
        jobList.forEach(j -> {
            try {
                j.close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private static void silentlyClose(SelectableChannel sc) {
        if (sc == null)
            return;
        try {
            sc.close();
            throw new IOException();
        } catch (IOException e) {
            // Do nothing
        }
    }

    private static void usage() {
        System.out.println("JarRetServer <joblistPath> [configPath]");
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        if (args.length < 1 || args.length > 2) {
            usage();
            return;
        }

        Path configPath = (args.length == 1) ? null : Paths.get(args[1]);
        JarRetServer server = new JarRetServer(Paths.get(args[0]), configPath);

        System.out.println("Server listening on port " + args[0]);
//        JarRetServer server = new JarRetServer(7777, Paths.get("resources/JarRetJobs.json"));
        server.launch();
        server.closeAllMonitors();
    }

    private String interestOpsToString(SelectionKey key) {
        if (!key.isValid()) {
            return "CANCELLED";
        }
        int interestOps = key.interestOps();
        ArrayList<String> list = new ArrayList<>();
        if ((interestOps & SelectionKey.OP_ACCEPT) != 0) list.add("OP_ACCEPT");
        if ((interestOps & SelectionKey.OP_READ) != 0) list.add("OP_READ");
        if ((interestOps & SelectionKey.OP_WRITE) != 0) list.add("OP_WRITE");
        return String.join("|", list);
    }

    public void printKeys() {
        Set<SelectionKey> selectionKeySet = selector.keys();
        if (selectionKeySet.isEmpty()) {
            System.out.println("The selector contains no key : this should not happen!");
            return;
        }
        System.out.println("The selector contains:");
        for (SelectionKey key : selectionKeySet) {
            SelectableChannel channel = key.channel();
            if (channel instanceof ServerSocketChannel) {
                System.out.println("\tKey for ServerSocketChannel : " + interestOpsToString(key));
            } else {
                SocketChannel sc = (SocketChannel) channel;
                System.out.println("\tKey for Client " + remoteAddressToString(sc) + " : " + interestOpsToString(key));
            }
        }
    }

    private String remoteAddressToString(SocketChannel sc) {
        try {
            return sc.getRemoteAddress().toString();
        } catch (IOException e) {
            return "???";
        }
    }

    private void printSelectedKey() {
        if (selectedKeys.isEmpty()) {
            System.out.println("There were not selected keys.");
            return;
        }
        System.out.println("The selected keys are :");
        for (SelectionKey key : selectedKeys) {
            SelectableChannel channel = key.channel();
            if (channel instanceof ServerSocketChannel) {
                System.out.println("\tServerSocketChannel can perform : " + possibleActionsToString(key));
            } else {
                SocketChannel sc = (SocketChannel) channel;
                System.out.println("\tClient " + remoteAddressToString(sc) + " can perform : " + possibleActionsToString(key));
            }

        }
    }

    private String possibleActionsToString(SelectionKey key) {
        if (!key.isValid()) {
            return "CANCELLED";
        }
        ArrayList<String> list = new ArrayList<>();
        if (key.isAcceptable()) list.add("ACCEPT");
        if (key.isReadable()) list.add("READ");
        if (key.isWritable()) list.add("WRITE");
        return String.join(" and ", list);
    }
}
