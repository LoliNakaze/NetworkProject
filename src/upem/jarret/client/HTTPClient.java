package upem.jarret.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;

import upem.jarret.worker.Worker;
import upem.jarret.worker.WorkerFactory;

/**
 * Created by nakaze on 23/03/17.
 */
public class HTTPClient {
    static final Charset ASCII = Charset.forName("ASCII");

    ObjectMapper map;

    public static void main(String[] args)
            throws IOException, ClassNotFoundException, IllegalAccessException, InstantiationException {
        String host = "ns3001004.ip-5-196-73.eu";
        int port = 8080;
        SocketChannel sc = SocketChannel.open(new InetSocketAddress(host, port));

        String request = "GET Task HTTP/1.1\r\nHOST:  " + host + "\r\n" + "\r\n";

        System.out.println("Voici mon Client qui parle �: " + sc.getRemoteAddress() + "\nRequest ->\n" + request);

        sc.write(ASCII.encode(request));

        HTTPReader reader = new HTTPReader(sc, ByteBuffer.allocate(50));
        HTTPHeader header = reader.readHeader();

        int contentLength = header.getContentLength();

        System.out.println(header);
        System.out.println("avant read");

        StringBuilder contenu = new StringBuilder();

        ByteBuffer content = reader.readBytes(contentLength);
        content.flip();
        contenu.append(header.getCharset().decode(content));
        System.out.println("\n\n" + contenu);


        ObjectMapper mapper = new ObjectMapper();
        HashMap<String, Object> map = mapper.readValue(contenu.toString(), HashMap.class);

        Worker instance = null;
        try {
            instance = WorkerFactory.getWorker((String) map.get("WorkerURL"), (String) map.get("WorkerClassName"));
        } catch (Exception e) {
            System.err.println("Instance a foir�");
        }
        Integer numTask;
        String jsonAnswer = null;

        try {
            numTask = Integer.valueOf(((String) map.get("Task")));
            jsonAnswer = instance.compute(numTask);
        } catch (Exception e) {
            System.err.println("Compute a foir�");
        }

        map.put("Answer", jsonAnswer);
        map.put("ClientId", "Seeeerges");

        String retourAuJson = mapper.writeValueAsString(map);
        System.out.println(retourAuJson);

        StringBuilder sb = new StringBuilder();

        String jobId = (String) map.get("JobId");
        String workerVersion = (String) map.get("WorkerVersion");
        String workerUrl = (String) map.get("WorkerURL");
        String workerClassName = (String) map.get("WorkerClassName");
        String task = (String) map.get("Task");
        String clientId = (String) map.get("ClientId");

        sb.append("{\"JobId\": \"").append(jobId).append("\", \"WorkerVersion\": \"").append(workerVersion)
                .append("\", \"WorkerURL\": \"").append(workerUrl).append("\", \"WorkerClassName\": \"")
                .append(workerClassName).append("\", \"Task\": \"").append(task).append("\", \"ClientId\": \"")
                .append(clientId).append("\", \"Answer\": ").append(retourAuJson).append("}");

        System.out.println("Voici le JSON POUR LE POST -> \n " + sb.toString());
        String corpsJson = sb.toString();

        String headerForPost = createHeaderToPost(sc.getRemoteAddress(), Long.BYTES + Integer.BYTES + corpsJson.length());

        System.out.println(headerForPost);

        ByteBuffer buffToSend = ByteBuffer.allocate(4096);

        buffToSend.put(ASCII.encode(headerForPost));
        buffToSend.putLong(Long.valueOf(jobId));
        buffToSend.putInt(Integer.valueOf(task));
        buffToSend.put(header.getCharset().encode(corpsJson));

        buffToSend.flip();

        sc.write(buffToSend);

        ByteBuffer bu = ByteBuffer.allocate(1024);
        sc.read(bu);
        bu.flip();
        System.out.println(ASCII.decode(bu).toString());
        sc.close();
    }

    static String createHeaderToPost(SocketAddress socketAddress, int contentLenght) {
        StringBuilder sb = new StringBuilder();

        sb.append("POST Answer HTTP/1.1\r\n")
                .append("Host: ").append(socketAddress).append("\r\n")
                .append("Content-Type: application/json\r\n")
                .append("Content-Length: ").append(contentLenght).append("\r\n")
                .append("\r\n");

        return sb.toString();
    }

//    private static final Charset ASCII = Charset.forName("ASCII");
//    private final InetSocketAddress server;
//    private final String resource;
//    private SocketChannel channel = SocketChannel.open();
//    private boolean closed = false;
//
//    HTTPClient(String address, int port, String resource) throws IOException {
//        this.server = new InetSocketAddress(address, 80);
//        this.resource = resource;
//    }
//
//    String get() throws IOException {
//        int bufferSize = 1024;
//        channel.connect(server);
//        channel.write(ASCII.encode(resource));
//
//        HTTPReader reader = new HTTPReader(channel, ByteBuffer.allocate(bufferSize));
//        HTTPHeader header = reader.readHeader();
//
//        String cl = header.getFields().get("Content-Length");
//        if (cl == null)
//            // TODO Chunked
//            return null;
//        return ASCII.decode(reader.readBytes(Integer.parseInt(cl))).toString();
//    }
//
//    void close() throws IOException {
//        channel.close();
//        closed = true;
//    }
//
//    boolean write(String response) throws IOException {
//        if (closed)
//            return false;
//        channel.write(ASCII.encode(response));
//        return true;
//    }

>>>>>>>2d4b0d10c29699080185648608c13f59ed0b9afc
}
