package upem.jarret.client;

import upem.jarret.http.HTTPHeader;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;

/**
 * Created by nakaze on 23/03/17.
 */
public class HTTPClient {
    private static final Charset ASCII = Charset.forName("ASCII");
    private final InetSocketAddress server;
    private final String resource;
    private SocketChannel channel = SocketChannel.open();
    private boolean closed = false;

    HTTPClient(String address, int port, String resource) throws IOException {
        this.server = new InetSocketAddress(address, 80);
        this.resource = resource;
    }

    String get() throws IOException {
        int bufferSize = 1024;
        channel.connect(server);
        channel.write(ASCII.encode(resource));

        HTTPReader reader = HTTPReader.useBlockingReader(channel, ByteBuffer.allocate(bufferSize));
        HTTPHeader header = reader.readHeader();

        String cl = header.getFields().get("Content-Length");
        if (cl == null)
            // TODO Chunked
            return null;
        return ASCII.decode(reader.readBytes(Integer.parseInt(cl))).toString();
    }

    void close() throws IOException {
        channel.close();
        closed = true;
    }

    boolean write(String response) throws IOException {
        if (closed)
            return false;
        channel.write(ASCII.encode(response));
        return true;
    }
}
