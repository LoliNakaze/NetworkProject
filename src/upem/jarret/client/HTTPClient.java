package upem.jarret.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;

/**
 * Created by nakaze on 23/03/17.
 */
public class HTTPClient {
    private final InetSocketAddress server;
    private final String resource;

    HTTPClient(String address, String resource) {
        this.server = new InetSocketAddress(address, 80);
        this.resource = resource;
    }

    String get() throws IOException {
        int bufferSize = 1024;
        Charset ascii = Charset.forName("ASCII");
        try (SocketChannel channel = SocketChannel.open()) {
            channel.connect(server);
            channel.write(ascii.encode(resource));

            HTTPReader reader = new HTTPReader(channel, ByteBuffer.allocate(bufferSize));
            HTTPHeader header = reader.readHeader();

            String cl = header.getFields().get("Content-Length");
            if (cl == null)
                // TODO Chunked
                return null;

            return ascii.decode(reader.readBytes(Integer.parseInt(cl))).toString();
        }
    }
}
