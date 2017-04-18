package upem.jarret.http;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

class BlockingHTTPReader implements HTTPReader {
    private final Charset ASCII_CHARSET = Charset.forName("ASCII");
    private final SocketChannel sc;
    private final ByteBuffer buff;

    BlockingHTTPReader(SocketChannel sc, ByteBuffer buff) {
        this.sc = sc;
        this.buff = buff;
    }

    public HTTPHeader readHeader() throws IOException {
        String response = readLineCRLF();
        Map<String, String> fields = new HashMap<>();
        String line;

		/*
         * On continue tant que l'on ne trouve pas LA FAMEUSE LIGNE VIDE !!!
		 * APRES LA LIGNE C'EST LE CORPS !
		 */
        while (!(line = readLineCRLF()).isEmpty()) {
            String[] strings = line.split(":");
            fields.merge(
                    strings[0],
                    Arrays.stream(strings).skip(1).collect(Collectors.joining(":")),
                    (x, y) -> x + ";" + y);
        }

        return HTTPHeaderFromServer.create(response, fields);
    }

    @Override
    public String readLineCRLF() throws IOException {
        byte last = 0;
        byte llast = 0;
        StringBuilder sb = new StringBuilder();

        buff.flip();

        while (!(llast == '\r' && last == '\n')) {
            if (!buff.hasRemaining()) {
                buff.flip();
                sb.append(ASCII_CHARSET.decode(buff));
                buff.clear();
                if (-1 == sc.read(buff)) {
                    throw new HTTPException();
                }
                buff.flip();
            }
            llast = last;
            last = buff.get();
        }
        int limit = buff.limit();
        buff.flip();
        sb.append(ASCII_CHARSET.decode(buff));
        sb.delete(sb.length() - 2, sb.length());

        buff.limit(limit);
        /*Permet de traiter plus tard les données dans le buffer après \r\n */
        buff.compact();

        return sb.toString();
    }

    @Override
    public ByteBuffer readBytes(int contentLength) throws IOException {
        ByteBuffer bufferFullContent = ByteBuffer.allocate(contentLength);
        int nbBytesRead = 0;
        do {
            buff.flip();

            int nbByteLeftToRead = contentLength - nbBytesRead;

			/*
             * Il y a trop de matos dans le buffer, on veut s'arreter au bon
			 * moment et ne pas perdre l'information en trop
			 */
            if (nbByteLeftToRead < buff.remaining()) {
                int limit = buff.limit();
                buff.limit(buff.position() + nbByteLeftToRead);
                bufferFullContent.put(buff);
                buff.limit(limit);
                nbBytesRead = contentLength;
                break;
                /* on sors de la boucle */
            }
            nbBytesRead += buff.remaining();
            bufferFullContent.put(buff);
            buff.clear();
            if (nbBytesRead >= contentLength) {
                break;
            }
            sc.read(buff);
        } while (nbBytesRead < contentLength);

        return bufferFullContent;
    }

    /**
     * @return a ByteBuffer in write-mode containing a content read in chunks
     * mode
     * @throws IOException HTTPException if the connection is closed before the end of
     *                     the chunks if chunks are ill-formed
     */

    public ByteBuffer readChunks() throws IOException {
        // TODO
        return null;
    }

    public static void main(String[] args) throws IOException {
        Charset charsetASCII = Charset.forName("ASCII");
        String request = "GET / HTTP/1.1\r\n" + "Host: www.w3.org\r\n" + "\r\n";
        SocketChannel sc = SocketChannel.open();

		/* On a une connection bi-directionnelle avec www.w3.org */
        sc.connect(new InetSocketAddress("www.w3.org", 80));

        sc.write(charsetASCII.encode(request));

        ByteBuffer bb = ByteBuffer.allocate(50);
        HTTPReader reader = HTTPReader.useBlockingReader(sc, bb);

//		System.out.println(reader.readLineCRLF());
//		System.out.println(reader.readLineCRLF());
//		System.out.println(reader.readLineCRLF());
//		sc.close();
//
//		/*------------------------------------------------------*/
//
//		bb = ByteBuffer.allocate(50);
//		sc = SocketChannel.open();
//		sc.connect(new InetSocketAddress("www.w3.org", 80));
//		reader = new HTTPReader(sc, bb);
//		sc.write(charsetASCII.encode(request));
//		System.out.println(reader.readHeader());
//		sc.close();

		/*------------------------------------------------------*/

        bb = ByteBuffer.allocate(50);
        sc = SocketChannel.open();
        sc.connect(new InetSocketAddress("www.w3.org", 80));
        reader = HTTPReader.useBlockingReader(sc, ByteBuffer.allocate(50));
        sc.write(charsetASCII.encode(request));
        HTTPHeader header = reader.readHeader();
        System.out.println(header);
        ByteBuffer content = reader.readBytes(header.getContentLength());
        content.flip();
        System.out.println(header.getCharset().decode(content));
        sc.close();

		/*------------------------------------------------------*/
        /*
         * bb = ByteBuffer.allocate(50); request = "GET / HTTP/1.1\r\n" +
		 * "Host: www.u-pem.fr\r\n" + "\r\n"; sc = SocketChannel.open();
		 * sc.connect(new InetSocketAddress("www.u-pem.fr", 80)); reader = new
		 * HTTPReader(sc, bb); sc.write(charsetASCII.encode(request)); header =
		 * reader.readHeader(); System.out.println(header); content =
		 * reader.readChunks(); content.flip();
		 * System.out.println(header.getCharset().decode(content)); sc.close();
		 */
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(Charset.forName("UTF-8").decode(buff));
        return sb.toString();
    }

}