package upem.jarret.http;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by nakaze on 18/04/17.
 */
public interface HTTPHeader {
    static HTTPHeader createClientHeader(String response, Map<String, String> fields) throws HTTPException {
        return HTTPHeaderFromClient.create(response, fields);
    }

    static HTTPHeader createServerHeader(String response, Map<String, String> fields) throws HTTPException {
        return HTTPHeaderFromServer.create(response, fields);
    }

    String getResponse();

    String getVersion();

    Map<String, String> getFields();

    int getContentLength() throws HTTPException;

    String getContentType();

    Charset getCharset();

    boolean isChunkedTransfer();

    String toString();
}
