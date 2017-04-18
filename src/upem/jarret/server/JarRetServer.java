package upem.jarret.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import upem.jarret.client.HTTPHeader;
import upem.jarret.client.HTTPReader;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * Created by nakaze on 15/04/17.
 */
public class JarRetServer {
    private enum Command {
        SHUTDOWN, STOP, FLUSH, SHOW
    }

    static class Context {
        private boolean inputClosed = false;
        private final ByteBuffer buffer = ByteBuffer.allocate(BUF_SIZE);
        private final SelectionKey key;
        private final SocketChannel sc;
        private JobMonitor jobMonitor = null;
        private StringBuilder response = new StringBuilder();
        private static final Charset CHARSET_ASCII = Charset.forName("ASCII");

        private State state;

        enum State {
            CONNECTION, TASK, RESPONSE, END
        }

        public Context(SelectionKey key) {
            this.key = key;
            this.sc = (SocketChannel) key.channel();
            state = State.CONNECTION;
        }

        public void doRead() throws IOException {
            if (sc.read(buffer) == -1) {
                inputClosed = true;
            }

            switch (state) {
                case CONNECTION:
                    // TODO : Read the header and check format
                    break;
                case RESPONSE:
                    // TODO : Read the header and the response.
                    break;
                default:
                    throw new IllegalStateException("Impossible state in read mode: " + state.toString());
            }
        }

        public void doWrite() throws IOException {
            buffer.flip();
            sc.write(buffer);
            buffer.compact();

            switch (state) {
                case TASK:
                    // TODO : Send the task
                    break;
                case END:
                    // TODO : Send whether the response was fine.
                    break;
                default:
                    throw new IllegalStateException("Impossible state in write mode: " + state.toString());
            }
        }

        private void analyzeAnswer() throws IOException {
            //
            HTTPReader reader = HTTPReader.useStringReader(buffer);
            HTTPHeader header = reader.readHeader();

            ObjectMapper mapper = new ObjectMapper();
            HashMap<String, Object> map = mapper.readValue(reader.readBytes(header.getContentLength()).flip().toString(), HashMap.class);

            switch (state) {
                case CONNECTION:
                    buffer.clear();
                    String[] split = header.getResponse().split(" ");
                    if (!(split[0].equals("GET") && split[1].equals("Task"))) {
                        buffer.put(CHARSET_ASCII.encode(badRequest()));
                        state = State.END;
                    } else {
                        Charset contentCharset = (header.getCharset() != null) ? header.getCharset() : CHARSET_ASCII;
                        String task = jobMonitor.sendTask();
                        buffer.put(CHARSET_ASCII.encode(ok()));
                        buffer.put(CHARSET_ASCII.encode("Content-type: application/json; charset=utf-8\r\n"
                                + "Content-length: " + task.length() + "\r\n"
                                + "\r\n"));
                        buffer.put(contentCharset.encode(task));

                        state = State.TASK;
                    }
                    break;
                case RESPONSE:
                    Object answer = map.get("Answer");

                    buffer.clear();
                    if (answer == null) {
                        Object error = map.get("Error");
                        buffer.clear();

                        if (error == null) {
                            buffer.put(header.getCharset().encode(badRequest()));
                        } else {
                            // TODO : A demander : est-ce qu'une erreur dans une task fait que la task est exécutée ?
                            buffer.put(header.getCharset().encode(ok()));
                        }
                    } else {
                        jobMonitor.updateATask(Integer.parseInt((String) map.get("Task")), (String) answer);
                    }

                    state = State.END;
                    break;
            }

            key.interestOps(SelectionKey.OP_WRITE);
        }

        private String badRequest() {
            return "HTTP/1.1 400 Bad Request\r\n";
        }

        private String ok() {
            return "HTTP/1.1 200 OK\r\n";
        }

        private String comeback() {
            return ok()
                    + "Content-type: application/json; charset=utf-8\r\n"
                    + "Content-length: 199\r\n"
                    + "\r\n"
                    + "{"
                    + "\"ComeBackInSeconds\" : 300"
                    + "}";
        }
    }

    private static final int BUF_SIZE = 4096;
    private final ServerSocketChannel serverSocketChannel;
    private final Selector selector;
    private final Set<SelectionKey> selectedKeys;
    private final List<JobMonitor> jobList;

    private final Random rand = new Random();
    private final Thread listener = new Thread(() -> startCommandListener(System.in));

    private final ArrayBlockingQueue<Command> commandQueue = new ArrayBlockingQueue<>(5);
    private Command command;
    private Map<Command, Runnable> commandMap = new EnumMap<>(Command.class);
    private final Object lock = new Object();

    public JarRetServer(int port, Path path) throws IOException {
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress(port));
        selector = Selector.open();
        selectedKeys = selector.selectedKeys();

        jobList = JobMonitor.jobMonitorListFromFile(path);

        commandMap.put(Command.STOP, () -> silentlyClose(serverSocketChannel));
        commandMap.put(Command.FLUSH, () -> selector.keys().stream().filter(s -> !(s.channel() instanceof ServerSocketChannel)).forEach(k -> silentlyClose(k.channel())));
        commandMap.put(Command.SHUTDOWN, () -> {
            listener.interrupt();
            Thread.currentThread().interrupt();
        });
        commandMap.put(Command.SHOW, this::printKeys);
    }

    public void launch() throws IOException {
        listener.start();

        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        Set<SelectionKey> selectedKeys = selector.selectedKeys();
        while (!Thread.interrupted()) {
            selector.select();

            command = commandQueue.poll();
            if (command != null) commandMap.get(command).run();

            processSelectedKeys();
            selectedKeys.clear();
        }
    }

    private void startCommandListener(InputStream in) {
        Scanner scanner = new Scanner(in);

        while (!Thread.interrupted() && scanner.hasNextLine()) {
            String line = scanner.nextLine();

            switch (line) {
                case "SHUTDOWN":
                case "SHOW":
                case "STOP":
                case "FLUSH":
                    synchronized (lock) {
                        command = Command.valueOf(line);
                    }
                    selector.wakeup();
                    break;
                default:
                    System.err.println("Unknown command.");
                    break;
            }
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

    private static void silentlyClose(SelectableChannel sc) {
        if (sc == null)
            return;
        try {
            sc.close();
        } catch (IOException e) {
            // silently ignore
        }
    }

    private static void usage() {
        System.out.println("ServerSumNew <listeningPort> <joblistPath>");
    }

    public static void main(String[] args) throws NumberFormatException, IOException {
        if (args.length != 1) {
            usage();
            return;
        }
        JarRetServer server = new JarRetServer(Integer.parseInt(args[0]), Paths.get(args[1]));

        server.launch();
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