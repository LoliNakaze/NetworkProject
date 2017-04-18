package upem.jarret.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;

/**
 * Created by nakaze on 23/03/17.
 */
public class HTTPClientGuy {
    private final InetSocketAddress server;
    private final String resource;

    HTTPClientGuy(String address, String resource) {
        this.server = new InetSocketAddress(address, 8080);
        this.resource = resource;
    }

    public String get() throws IOException {
        int bufferSize = 1024;
        Charset ascii = Charset.forName("ASCII");
        try (SocketChannel channel = SocketChannel.open()) {
            channel.connect(server);
            channel.write(ascii.encode(resource));

            HTTPReader reader = HTTPReader.useBlockingReader(channel, ByteBuffer.allocate(bufferSize));
            HTTPHeader header = reader.readHeader();

            String cl = header.getFields().get("Content-Length");
            if (cl == null)
                // TODO Chunked
                return null;

            return ascii.decode(reader.readBytes(Integer.parseInt(cl))).toString();
        }
    }
    
    public static void main(String[] args) throws IOException {
		String host = "ns3001004.ip-5-196-73.eu";
		String request = "GET Task HTTP/1.1\r\nHOST: " + host + "\r\n" + "\r\n";

		HTTPClientGuy client = new HTTPClientGuy(host, request);
		
		client.get();
	}
}
