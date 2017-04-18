package upem.jarret.http;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/**
 * Created by nakaze on 15/04/17.
 */
public interface HTTPReader {
    /**
     * @return The HTTPHeaderFromClient object corresponding to the header read
     * @throws IOException HTTPException if the connection is closed before a header
     *                     could be read if the header is ill-formed
     */
    HTTPHeader readHeader () throws IOException;

    /**
     * @param size
     * @return a ByteBuffer in write-mode containing size bytes read on the
     * socket
     * @throws IOException HTTPException is the connection is closed before all bytes
     *                     could be read
     */
    ByteBuffer readBytes(int size) throws IOException;

    /**
     * @return The ASCII string terminated by CRLF
     * <p>
     * The method assume that buff is in write mode and leave it in
     * write-mode The method never reads from the socket as long as theç
     * buffer is not empty
     * @throws IOException HTTPException if the connection is closed before a line could
     *                     be read
     */
    String readLineCRLF() throws IOException;

    /**
     * Use an HTTPReader which use TCP in blocking mode.
     * @param sc The channel used to read the incoming bytes.
     * @param buff The buffer used to manipulate the incoming bytes.
     * @return The HTTPReader
     */
    static HTTPReader useBlockingReader(SocketChannel sc, ByteBuffer buff) {
        return new BlockingHTTPReader(sc, buff);
    }


    /**
     * Use an HTTPReader which use a fully-read ByteBuffer as an input stream.
     * @param buffer The buffer
     * @return The HTTPReader
     */
    static HTTPReader useStringReader(ByteBuffer buffer) {
        return new StringReader(buffer);
    }

/*    static HTTPReader useNonBlockingReader(SocketChannel sc, ByteBuffer buff) {
        return new NonBlockingHTTPReader(sc, buff);
    }*/
}
