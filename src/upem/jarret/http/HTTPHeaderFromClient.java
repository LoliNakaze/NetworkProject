package upem.jarret.http;

import java.util.*;


/**
 * @author carayol
 *         Class representing a HTTP header
 */

class HTTPHeaderFromClient extends HTTPHeaderAbstract implements HTTPHeader {
    private HTTPHeaderFromClient(String response, String version, Map<String, String> fields) throws HTTPException {
        this.response = response;
        this.version = version;
        this.fields = Collections.unmodifiableMap(fields);
    }
    
    static HTTPHeaderFromClient create(String response, Map<String, String> fields) throws HTTPException {
        String[] tokens = response.split(" ");
        // Treatment of the response line
        HTTPException.ensure(tokens.length >= 2, "Badly formed response:\n" + response);
        String version = tokens[2];
        HTTPException.ensure(SUPPORTED_VERSIONS.contains(version), "Unsupported version in response:\n" + response);

        Map<String,String> fieldsCopied = new HashMap<>();
        for (String s : fields.keySet())
            fieldsCopied.put(s,fields.get(s).trim());
        return new HTTPHeaderFromClient(response,version,fieldsCopied);
    }
}
