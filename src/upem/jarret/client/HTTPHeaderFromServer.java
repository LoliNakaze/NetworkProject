package upem.jarret.client;

import java.nio.charset.Charset;
import java.util.*;

import static upem.jarret.client.HTTPException.ensure;

/**
 * Created by nakaze on 18/04/17.
 */
public class HTTPHeaderFromServer implements HTTPHeader {
    /**
     * Supported versions of the HTTP Protocol
     */

    private final String response;
    private final String version;
    private final int code;
    private final Map<String, String> fields;


    private HTTPHeaderFromServer(String response, String version, int code, Map<String, String> fields) throws HTTPException {
        this.response = response;
        this.version = version;
        this.code = code;
        this.fields = Collections.unmodifiableMap(fields);
    }

    static HTTPHeaderFromServer create(String response, Map<String, String> fields) throws HTTPException {
        String[] tokens = response.split(" ");
        // Treatment of the response line
        ensure(tokens.length >= 2, "Badly formed response:\n" + response);
        String version = tokens[0];
        ensure(HTTPHeaderFromClient.SUPPORTED_VERSIONS.contains(version), "Unsupported version in response:\n" + response);
        int code = 0;
        try {
            code = Integer.valueOf(tokens[1]);
            ensure(code >= 100 && code < 600, "Invalid code in response:\n" + response);
        } catch (NumberFormatException e) {
            ensure(false, "Invalid response:\n" + response);
        }
        Map<String, String> fieldsCopied = new HashMap<>();
        for (String s : fields.keySet())
            fieldsCopied.put(s, fields.get(s).trim());
        return new HTTPHeaderFromServer(response, version, code, fieldsCopied);
    }

    @Override
    public String getResponse() {
        return response;
    }

    @Override
    public String getVersion() {
        return version;
    }

    public int getCode() {
        return code;
    }

    @Override
    public Map<String, String> getFields() {
        return fields;
    }

    /**
     * @return the value of the Content-Length field in the header
     * -1 if the field does not exists
     * @throws HTTPException when the value of Content-Length is not a number
     */
    @Override
    public int getContentLength() throws HTTPException {
        String s = fields.get("Content-Length");
        if (s == null) return -1;
        else {
            try {
                return Integer.valueOf(s.trim());
            } catch (NumberFormatException e) {
                throw new HTTPException("Invalid Content-Length field value :\n" + s);
            }
        }
    }

    /**
     * @return the Content-Type
     * null if there is no Content-Type field
     */
    @Override
    public String getContentType() {
        String s = fields.get("Content-Type");
        if (s != null) {
            return s.split(";")[0].trim();
        } else
            return null;
    }

    /**
     * @return the charset corresponding to the Content-Type field
     * null if charset is unknown or unavailable on the JVM
     */
    @Override
    public Charset getCharset() {
        Charset cs = null;
        String s = fields.get("Content-Type");
        if (s == null) return cs;
        for (String t : s.split(";")) {
            if (t.contains("charset=")) {
                try {
                    cs = Charset.forName(t.split("=")[1].trim());
                } catch (Exception e) {
                    // If the Charset is unknown or unavailable we turn null
                }
                return cs;
            }
        }
        return cs;
    }

    /**
     * @return true if the header correspond to a chunked response
     */
    @Override
    public boolean isChunkedTransfer() {
        return fields.containsKey("Transfer-Encoding") && fields.get("Transfer-Encoding").trim().equals("chunked");
    }

    @Override
    public String toString() {
        return response + "\n"
                + version + " " + code + "\n"
                + fields.toString();
    }
}