package upem.jarret.http;

import java.util.*;

/**
 * Created by nakaze on 18/04/17.
 */
class HTTPHeaderFromServer extends HTTPHeaderAbstract implements HTTPHeader {
    /**
     * Supported versions of the HTTP Protocol
     */

    private final int code;

    private HTTPHeaderFromServer(String response, String version, int code, Map<String, String> fields) throws HTTPException {
        this.response = response;
        this.version = version;
        this.code = code;
        this.fields = Collections.unmodifiableMap(fields);
    }

    static HTTPHeaderFromServer create(String response, Map<String, String> fields) throws HTTPException {
        String[] tokens = response.split(" ");
        // Treatment of the response line
        HTTPException.ensure(tokens.length >= 2, "Badly formed response:\n" + response);
        String version = tokens[0];
        HTTPException.ensure(SUPPORTED_VERSIONS.contains(version), "Unsupported version in response:\n" + response);
        int code = 0;
        try {
            code = Integer.valueOf(tokens[1]);
            HTTPException.ensure(code >= 100 && code < 600, "Invalid code in response:\n" + response);
        } catch (NumberFormatException e) {
            HTTPException.ensure(false, "Invalid response:\n" + response);
        }
        Map<String, String> fieldsCopied = new HashMap<>();
        for (String s : fields.keySet())
            fieldsCopied.put(s, fields.get(s).trim());
        return new HTTPHeaderFromServer(response, version, code, fieldsCopied);
    }

    public int getCode() {
        return code;
    }
}