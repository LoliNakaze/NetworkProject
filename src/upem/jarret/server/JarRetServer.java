package upem.jarret.server;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Created by nakaze on 15/04/17.
 */
public class JarRetServer {
    enum Command {
        NONE, HALT, STOP, FLUSH, SHOW
    }

    static class Context {
        private boolean inputClosed = false;
        private final ByteBuffer buffer = ByteBuffer.allocate(BUF_SIZE);
        private final SelectionKey key;
        private final SocketChannel sc;

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
    }

    private static final int BUF_SIZE = 512;
    private final ServerSocketChannel serverSocketChannel;
    private final Selector selector;
    private final Set<SelectionKey> selectedKeys;
    private final List<JobMonitor> jobList;

    private final Thread listener = new Thread(() -> startCommandListener(System.in));

    private Command command = Command.NONE;
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
        commandMap.put(Command.HALT, () -> {
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

            synchronized (lock) {
                if (command.compareTo(Command.NONE) != 0) {
                    commandMap.get(command).run();
                    command = Command.NONE;
                }
            }

            processSelectedKeys();
            selectedKeys.clear();
        }
    }

    private void startCommandListener(InputStream in) {
        Scanner scanner = new Scanner(in);

        while (!Thread.interrupted() && scanner.hasNextLine()) {
            String line = scanner.nextLine();

            switch (line) {
                case "HALT":
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
