/*
package upem.jarret.client;

import upem.jarret.server.JarRetServer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static upem.jarret.client.StringReader.ASCII_CHARSET;

*/
/**
 * Created by nakaze on 18/04/17.
 *//*

public class NonBlockingHTTPReader implements HTTPReader {
    enum ReaderState {
        HEADER, CONTENT
    }

    private final ByteBuffer buffer;
    private ReaderState readerState;
    private StringBuilder stringBuilder = new StringBuilder("");
    private byte last = 0;
    private byte llast = 0;
    private int toRead;

    NonBlockingHTTPReader (SocketChannel sc, ByteBuffer buffer) {
        this.buffer = buffer;
        readerState = ReaderState.HEADER;
    }

    @Override
    public ByteBuffer readBytes(int size) throws IOException {
        return null;
    }

    public HTTPHeaderFromClient readHeader(List<String> stringList) throws HTTPException {
        Map<String, String> fields = new HashMap<>();
        stringList.stream().skip(1).forEach(line -> {
            String[] strings = line.split(":");

            fields.merge(
                    strings[0],
                    Arrays.stream(strings).skip(1).collect(Collectors.joining(":")),
                    (x, y) -> x + ";" + y);
        });
        return HTTPHeaderFromClient.create(stringList.get(0), fields);
    }

    private void setToEnd() {
        buffer.position(buffer.limit());
        buffer.limit(buffer.capacity());
    }

    @Override
    public String readLineCRLF() throws IOException {
        switch (readerState) {
            case HEADER:
                buffer.flip();
                while (buffer.hasRemaining() && !(llast == '\r' && last == '\n')) {
                    llast = last;
                    last = buffer.get();
                }

                setToEnd();

                if (last == '\r' && last == '\n') {
                    buffer.flip();
                    stringBuilder.append(ASCII_CHARSET.decode(buffer));
                    setToEnd();
                    stringBuilder.delete(stringBuilder.length() - 2, stringBuilder.length());
                    if (stringBuilder.length() == 0) {
                        readerState = ReaderState.CONTENT;

                        if (-1 == (toRead = header.getContentLength())) {
                            switch (state) {
                                case CONNECTION:
                                    state = JarRetServer.Context.State.TASK;
                                    break;
                                case RESPONSE:
                                    state = JarRetServer.Context.State.END;
                                    break;
                            }
                        }
                        return;
                    }
                    stringList.add(stringBuilder.toString());
                    stringBuilder = new StringBuilder("");
                    last = llast = 0;
                }
                break;
            case CONTENT:
                // TODO : Read the header and the response.
                break;
            default:
                throw new IllegalStateException("Impossible state in read mode: " + state.toString());
        }
    }
}
*/
