package fr.upem.net.tcp.http.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;


public class HTTPReader {
    private final Charset ASCII_CHARSET = Charset.forName("ASCII");
    private final SocketChannel sc;
    private final ByteBuffer buff;

    public HTTPReader(SocketChannel sc, ByteBuffer buff) {
        this.sc = sc;
        this.buff = buff;
    }

    /**
     * @return The ASCII string terminated by CRLF
     * <p>
     * The method assume that buff is in write mode and leave it in write-mode
     * The method never reads from the socket as long as the buffer is not empty
     * @throws IOException HTTPException if the connection is closed before a line could be read
     */
    public String readLineCRLF() throws IOException {
        buff.flip();
        byte llastRead = 0;
        byte lastRead = 0;
        StringBuilder s = new StringBuilder("");

        while (llastRead != '\r' || lastRead != '\n') {
            if(!buff.hasRemaining()) {
                s.append(ASCII_CHARSET.decode(buff.flip()));

                buff.clear();
                if (-1 == sc.read(buff))
                    throw new HTTPException();
                buff.flip();
            }
            llastRead = lastRead;
            lastRead = buff.get();
        }

        int limit = buff.limit();
        s.append(ASCII_CHARSET.decode(buff.flip()));
        s.delete(s.length() - 2, s.length());

        buff.limit(limit).compact();

        return s.toString();
    }

    /**
     * @return The HTTPHeader object corresponding to the header read
     * @throws IOException HTTPException if the connection is closed before a header could be read
     *                     if the header is ill-formed
     */
    public HTTPHeader readHeader() throws IOException {
        String response = readLineCRLF();
        Map<String, String> fields = new HashMap<>();
        String line;

        while(!(line = readLineCRLF()).isEmpty()) {
            String[] strings = line.split(":");
            fields.merge(strings[0], Arrays.stream(strings).skip(1).collect(Collectors.joining(":")), (x, y) -> x + ";" + y);
        }

        return HTTPHeader.create(response, fields);
    }

    /**
     * @param size
     * @return a ByteBuffer in write-mode containing size bytes read on the socket
     * @throws IOException HTTPException is the connection is closed before all bytes could be read
     */
    public ByteBuffer readBytes(int size) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(size);
        int count = 0;
        do {
            buff.flip();
            if (size - count < buff.remaining()) {
                int limit = buff.limit();
                buff.limit(buff.position() + size - count);
                buffer.put(buff);
                buff.limit(limit);
                count = size;
                break;
            }
            count += buff.remaining();
            buffer.put(buff);
            buff.clear();
            sc.read(buff);
        } while (count < size);

        return buffer;
    }

    /**
     * @return a ByteBuffer in write-mode containing a content read in chunks mode
     * @throws IOException HTTPException if the connection is closed before the end of the chunks
     *                     if chunks are ill-formed
     */

    public ByteBuffer readChunks() throws IOException {
        // TODO
        return null;
    }


    public static void main(String[] args) throws IOException {
        Charset charsetASCII = Charset.forName("ASCII");
        String request = "GET / HTTP/1.1\r\n"
                + "Host: www.w3.org\r\n"
                + "\r\n";
        SocketChannel sc = SocketChannel.open();
        sc.connect(new InetSocketAddress("www.w3.org", 80));
        sc.write(charsetASCII.encode(request));
        ByteBuffer bb = ByteBuffer.allocate(50);
        HTTPReader reader = new HTTPReader(sc, bb);
        System.out.println(reader.readLineCRLF());
        System.out.println(reader.readLineCRLF());
        System.out.println(reader.readLineCRLF());
        sc.close();

        bb = ByteBuffer.allocate(50);
        sc = SocketChannel.open();
        sc.connect(new InetSocketAddress("www.w3.org", 80));
        reader = new HTTPReader(sc, bb);
        sc.write(charsetASCII.encode(request));
        System.out.println(reader.readHeader());
        sc.close();

        bb = ByteBuffer.allocate(50);
        sc = SocketChannel.open();
        sc.connect(new InetSocketAddress("www.w3.org", 80));
        reader = new HTTPReader(sc, bb);
        sc.write(charsetASCII.encode(request));
        HTTPHeader header = reader.readHeader();
        System.out.println(header);
        ByteBuffer content = reader.readBytes(header.getContentLength());
        content.flip();
        System.out.println(header.getCharset().decode(content));
        sc.close();

        bb = ByteBuffer.allocate(50);
        request = "GET / HTTP/1.1\r\n"
                + "Host: www.u-pem.fr\r\n"
                + "\r\n";
        sc = SocketChannel.open();
        sc.connect(new InetSocketAddress("www.u-pem.fr", 80));
        reader = new HTTPReader(sc, bb);
        sc.write(charsetASCII.encode(request));
        header = reader.readHeader();
        System.out.println(header);
        content = reader.readChunks();
        content.flip();
        System.out.println(header.getCharset().decode(content));
        sc.close();
    }
}
