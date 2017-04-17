package upem.jarret.client;

import upem.jarret.worker.Worker;
import upem.jarret.worker.WorkerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.SocketAddress;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.map.ObjectMapper;

import jdk.internal.org.objectweb.asm.tree.TryCatchBlockNode;

public class JarRetClient {
	static final Charset ASCII = Charset.forName("ASCII");
	static final Charset UTF8 = Charset.forName("UTF-8");

	HashMap<Long, HashMap<String, Worker>> workers = new HashMap<>();

	static ObjectMapper mapper = new ObjectMapper();

	static String createGetRequest(SocketAddress serverAdress) {
		return "GET Task HTTP/1.1\r\nHOST:  " + serverAdress + "\r\n" + "\r\n";
	}

	public static void main(String[] args) throws IOException, InterruptedException {
		String host = "ns3001004.ip-5-196-73.eu";
		int port = 8080;
		SocketChannel sc = null;
		try {
			sc = SocketChannel.open(new InetSocketAddress(host, port));
		} catch (IOException e) {
			System.err.println("La connexion a echoué");
			e.printStackTrace();
		}
		JarRetClient jarRetClient = new JarRetClient();
		int i = 0;

		/*-------BOUCLE PRINCIPALE--------*/
		while (i < 5) {

			String GETRequest = createGetRequest(sc.getRemoteAddress());
			// System.out.println(GETRequest);

			sc.write(ASCII.encode(GETRequest));

			HTTPReader reader = new HTTPReader(sc, ByteBuffer.allocate(50));
			HTTPHeader getHeader = reader.readHeader();

			ByteBuffer contentJson = reader.readBytes(getHeader.getContentLength());
			contentJson.flip();
			ServerAnswer serverAnswer = new ServerAnswer(
					mapper.readValue(getHeader.getCharset().decode(contentJson).toString(), HashMap.class));
			
			int waitSeconds = 0;
			if ((waitSeconds = checkWait(serverAnswer)) != 0){
				System.out.println("ON DOIT ATTENDRE");
				Thread.sleep(waitSeconds*1000);
				continue;
			}

			Worker worker = jarRetClient.returnAWorker(serverAnswer.getJobId(), serverAnswer.getWorkerVersion(),
					serverAnswer);

			// System.out.println(worker);

			String computeAnswerJson = "";
			String finalLine = "";
			try {
				computeAnswerJson = worker.compute(serverAnswer.getTaskNumber());
			} catch (Exception e) {
				finalLine = "\"Error\" : \"Computation error\"";
			}
			
			serverAnswer.putClientId("NEIL");
			
			if (isNested(computeAnswerJson)) {
				finalLine = "\"Error\" : \"Answer is nested\"";
			}
			
			else if( isInvalidJson(computeAnswerJson)){
				finalLine = "\"Error\" : \"Answer is not valid JSON\"";
			}
			else if(finalLine.isEmpty()){
				finalLine =  new StringBuilder("\"Answer\": ").append(computeAnswerJson).toString();
			}
			
			String contentNoFinal = createContentWithoutFinalLine(serverAnswer);
			
			StringBuilder noFinalLine = new StringBuilder(contentNoFinal);
			noFinalLine.append(finalLine).append("}");
			
			String contentToSend = noFinalLine.toString();
		
			System.out.println(contentToSend);
			
			String headerPostString = createHeaderToPost(host, Long.BYTES + Integer.BYTES + contentToSend.length());
			
			
			ByteBuffer buffToSend = ByteBuffer.allocate(4096);
			buffToSend.put(ASCII.encode(headerPostString));
			buffToSend.putLong(serverAnswer.getJobId());
			buffToSend.putInt(serverAnswer.getTaskNumber());
			try {
				buffToSend.put(UTF8.encode(contentToSend.toString()));	
			} catch (BufferOverflowException e) {
				contentToSend= new StringBuilder(contentNoFinal).append(" \"Error\" : \"Too Long\"}").toString(); 
				headerPostString = createHeaderToPost(host, Long.BYTES + Integer.BYTES + contentToSend.length());

				buffToSend.clear();
				buffToSend.put(ASCII.encode(headerPostString));
				buffToSend.putLong(serverAnswer.getJobId());
				buffToSend.putInt(serverAnswer.getTaskNumber()); 
				buffToSend.put(UTF8.encode(contentToSend.toString()));	
			}
			
			
			buffToSend.flip();
			sc.write(buffToSend);

			ByteBuffer bu = ByteBuffer.allocate(1024);
			sc.read(bu);
			bu.flip();
			System.out.println(ASCII.decode(bu).toString());

			i++;
		}

		sc.close();

	}

	private static int checkWait(ServerAnswer sa) {
		Integer nbseconds = 0;
		if ( null == (nbseconds = Integer.valueOf( (String)  sa.map.get("ComeBackInSeconds") )) ) {
			return 0;
		}
		return nbseconds;
	}

	private static boolean isInvalidJson(String json) throws IOException {
		JsonNode node;
		try {
			node = mapper.readTree(json);
		} catch (JsonProcessingException e) {
			return true;
		}
		return false;
	}

	private static boolean isNested(String json) throws JsonProcessingException, IOException {
		Iterator<JsonNode> it = mapper.readTree(json).iterator();
		for(JsonNode node : it.next()){
			if(node.isObject()){
				return true;
			}
		}
		return false;
	}

	private static boolean isTooLong(String header, String json) {
		if (header.length() + Long.BYTES + Integer.BYTES + json.length() > 4080){
			return true;
		}
		return false;
	}

	
	static String createHeaderToPost(String adressServer, int contentLength) {
		StringBuilder sb = new StringBuilder();

		sb.append("POST Answer HTTP/1.1\r\n").append("Host: ").append(adressServer).append("\r\n")
				.append("Content-Type: application/json\r\n").append("Content-Length: ").append(contentLength)
				.append("\r\n").append("\r\n");

		return sb.toString();
	}
	
	static String createContentWithoutFinalLine(ServerAnswer sa) {
		StringBuilder jsonPost = new StringBuilder();
		jsonPost.append("{\"JobId\": \"").append(sa.getJobId()).append("\", \"WorkerVersion\": \"")
				.append(sa.getWorkerVersion()).append("\", \"WorkerURL\": \"").append(sa.getWorkerURL())
				.append("\", \"WorkerClassName\": \"").append(sa.getWorkerClassName()).append("\", \"Task\": \"")
				.append(sa.getTaskNumber()).append("\", \"ClientId\": \"").append(sa.getClientId()).append("\",");
		return jsonPost.toString();
	}
	


//	private static ByteBuffer createBufferForPost(String headerPost,String lastLine, ServerAnswer sa) {
//		StringBuilder jsonPost = new StringBuilder();
//		jsonPost.append("{\"JobId\": \"").append(sa.getJobId()).append("\", \"WorkerVersion\": \"")
//				.append(sa.getWorkerVersion()).append("\", \"WorkerURL\": \"").append(sa.getWorkerURL())
//				.append("\", \"WorkerClassName\": \"").append(sa.getWorkerClassName()).append("\", \"Task\": \"")
//				.append(sa.getTaskNumber()).append("\", \"ClientId\": \"").append(sa.getClientId())
//				.append("\", \"Answer\": ").append(sa.getComputeAnswerJson()).append("}");
//
//		System.out.println(jsonPost.toString());
//
//		String headerPost = createHeaderToPost(adressServer, Long.BYTES + Integer.BYTES + jsonPost.length());
//
//		ByteBuffer buffToSend = ByteBuffer.allocate(4096);
//		buffToSend.put(ASCII.encode(headerPost));
//		buffToSend.putLong(sa.getJobId());
//		buffToSend.putInt(sa.getTaskNumber());
//		buffToSend.put(UTF8.encode(jsonPost.toString()));
//		buffToSend.flip();
//		return buffToSend;
//	}

	private Worker returnAWorker(Long jobId, String workerVersion, ServerAnswer serverAnswer) {

		return workers.computeIfAbsent(jobId, __ -> new HashMap<String, Worker>()).computeIfAbsent(workerVersion,
				__ -> {
					try {
						System.out.println("PAS TROUVER ON CREER UN NOUVEAU");
						return WorkerFactory.getWorker(serverAnswer.getWorkerURL(), serverAnswer.getWorkerClassName());
					} catch (Exception e) {
						throw new IllegalArgumentException();
					}
				}

		);

	}

}
