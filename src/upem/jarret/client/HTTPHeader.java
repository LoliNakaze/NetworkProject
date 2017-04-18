package upem.jarret.client;

import java.nio.charset.Charset;
import java.util.*;

/**
 * Created by nakaze on 18/04/17.
 */
public interface HTTPHeader {
    String[] LIST_SUPPORTED_VERSIONS = new String[]{"HTTP/1.0", "HTTP/1.1", "HTTP/1.2"};
    Set<String> SUPPORTED_VERSIONS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(LIST_SUPPORTED_VERSIONS)));

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
