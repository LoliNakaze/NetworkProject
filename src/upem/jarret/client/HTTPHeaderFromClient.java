package upem.jarret.client;

import java.nio.charset.Charset;
import java.util.*;

import static upem.jarret.client.HTTPException.ensure;


/**
 * @author carayol
 *         Class representing a HTTP header
 */

public class HTTPHeaderFromClient implements HTTPHeader {
    /**
     * Supported versions of the HTTP Protocol
     */
    private final String response;
    private final String version;
    private final Map<String, String> fields;


    private HTTPHeaderFromClient(String response, String version, Map<String, String> fields) throws HTTPException {
        this.response = response;
        this.version = version;
        this.fields = Collections.unmodifiableMap(fields);
    }
    
    public static HTTPHeaderFromClient create(String response, Map<String,String> fields) throws HTTPException {
        String[] tokens = response.split(" ");
        // Treatment of the response line
        ensure(tokens.length >= 2, "Badly formed response:\n" + response);
        String version = tokens[2];
        ensure(HTTPHeaderFromClient.SUPPORTED_VERSIONS.contains(version), "Unsupported version in response:\n" + response);

        Map<String,String> fieldsCopied = new HashMap<>();
        for (String s : fields.keySet())
            fieldsCopied.put(s,fields.get(s).trim());
        return new HTTPHeaderFromClient(response,version,fieldsCopied);
    }

    public String getResponse() {
        return response;
    }

    public String getVersion() {
        return version;
    }

    public Map<String, String> getFields() {
        return fields;
    }

    /**
     * @return the value of the Content-Length field in the header
     *         -1 if the field does not exists
     * @throws HTTPException when the value of Content-Length is not a number
     */
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
     *         null if there is no Content-Type field
     */
    public String getContentType() {
        String s = fields.get("Content-Type");
        if (s != null) {
            return s.split(";")[0].trim();
        } else
            return null;
    }

    /**
     * @return the charset corresponding to the Content-Type field
     *         null if charset is unknown or unavailable on the JVM
     */
    public Charset getCharset() {
        Charset cs = null;
        String s = fields.get("Content-Type");
        if (s == null) return cs;
        for (String t : s.split(";")) {
            if (t.contains("charset=")) {
                try {
                    cs= Charset.forName(t.split("=")[1].trim());
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
    public boolean isChunkedTransfer() {
        return fields.containsKey("Transfer-Encoding") && fields.get("Transfer-Encoding").trim().equals("chunked");
    }

    public String toString() {
        return response + "\n"
                + version + " " + "\n"
                + fields.toString();
    }
}
