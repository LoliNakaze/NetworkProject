package upem.jarret.client;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/**
 * Created by nakaze on 15/04/17.
 */
public interface HTTPReader {
    HTTPHeader readHeader() throws IOException;

    ByteBuffer readBytes(int size) throws IOException;

    String readLineCRLF() throws IOException;

    static HTTPReader useBlockingReader(SocketChannel sc, ByteBuffer buff) {
        return new BlockingHTTPReader(sc, buff);
    }
}
