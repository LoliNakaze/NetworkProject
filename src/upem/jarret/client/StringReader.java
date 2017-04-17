package upem.jarret.client;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

/**
 * Created by nakaze on 17/04/17.
 */
class StringReader implements HTTPReader {
    public static final Charset ASCII_CHARSET = Charset.forName("ASCII");
    private final ByteBuffer buffer;

    StringReader(ByteBuffer buffer) {
        this.buffer = buffer;
    }

    @Override
    public ByteBuffer readBytes(int size) throws IOException {
        ByteBuffer bufferFullContent = ByteBuffer.allocate(size);

        buffer.flip();

        if (size >= buffer.remaining()) {
            throw new IllegalArgumentException("Response too long");
        }

        buffer.limit(buffer.position() + size);
        bufferFullContent.put(buffer);
        buffer.clear();

        return bufferFullContent;
    }

    @Override
    public String readLineCRLF() throws IOException {
        byte last = 0;
        byte llast = 0;
        StringBuilder sb = new StringBuilder();

        buffer.flip();

        while (!(llast == '\r' && last == '\n')) {
            if (!buffer.hasRemaining()) {
                throw new IllegalArgumentException("Response too long");
            }
            llast = last;
            last = buffer.get();
        }

        int limit = buffer.limit();
        buffer.flip();
        sb.append(ASCII_CHARSET.decode(buffer));
        sb.delete(sb.length() - 2, sb.length());

        buffer.limit(limit);
    /*Permet de traiter plus tard les données dans le buffer après \r\n */
        buffer.compact();

        return sb.toString();
    }
}
