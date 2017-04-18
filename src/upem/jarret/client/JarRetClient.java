package upem.jarret.client;

import upem.jarret.http.HTTPHeader;
import upem.jarret.http.HTTPReader;
import upem.jarret.worker.Worker;
import upem.jarret.worker.WorkerFactory;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Iterator;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.map.ObjectMapper;

public class JarRetClient {
	static final Charset ASCII = Charset.forName("ASCII");
	static final Charset UTF8 = Charset.forName("UTF-8");

	HashMap<Long, HashMap<String, Worker>> workers = new HashMap<>();

	static ObjectMapper mapper = new ObjectMapper();
	
	
	/**Create a GET request that aim the server at serverAdress
	 * @param serverAdress : The adress of the server
	 * @return the GET request as a String
	 */
	static String createGetRequest(SocketAddress serverAdress) {
		return "GET Task HTTP/1.1\r\nHOST:  " + serverAdress + "\r\n" + "\r\n";
	}


	/** Display the server's answer. This function is meant to be called after the POST request to check if everything was okay.
	 * @param sc socketchannel used to read the answer of the server the client's POST
	 * @throws IOException
	 */
	private static void readServerAnswerAfterPost(SocketChannel sc) throws IOException {
		ByteBuffer bu = ByteBuffer.allocate(1024);
		sc.read(bu);
		bu.flip();
		System.out.println(ASCII.decode(bu).toString());
	}

	/** Return the last line of the content of the JSON inside the content of the POST request.
	 * 	The last line can be
	 * 1) the JSON answer return by Worker.compute(nbtask)
	 * 2)" Error : computation "error if compute throwed an exception
	 * 3)" Error : Answer is not valid JSON " if Worker.compute() returned an invalid JSON
	 * 4)" Error: Answer is nested " if Worker.compute() returned a nested JSON
	 * @param worker 			The instance of a class implementing Worker
	 * @param taskNumber	The task that worker should compute
	 * @return
	 * @throws IOException
	 */
	private static String computeLastLine(Worker worker, int taskNumber) throws IOException {
		String computeAnswer = "";
		String finalLine = "";
		try {
			computeAnswer = worker.compute(taskNumber);
		} catch (Exception e) {
			finalLine = "\"Error\" : \"Computation error\"}";
		}
		if (isInvalidJson(computeAnswer)) {
			finalLine = "\"Error\" : \"Answer is not valid JSON\"}";
		}
		else if (isNested(computeAnswer)) {
			finalLine = "\"Error\" : \"Answer is nested\"}";
		} else if (finalLine.isEmpty()) {
			finalLine = new StringBuilder("\"Answer\": ").append(computeAnswer).append("}").toString();
		}

		return finalLine;
	}

	/**
	 * @param host				the address of the server 
	 * @param serverAnswer		Contain a map with all the informations contained in the initial answer of the server
	 * @param contentWithoutLastLine	all the Json to send to the server without the last line. We'll use it in case we need to create a modify the last line of the request
	 * @param contentToSend	All the JSON that will be the content of the request.
	 * @return the byte buffer containing the POST request
	 */
	private static ByteBuffer createBufferToSend(String host, ServerAnswer serverAnswer, String contentWithoutLastLine,
			String contentToSend) {
		String headerPostString = createHeaderToPost(host, Long.BYTES + Integer.BYTES + contentToSend.length());
		ByteBuffer buffToSend = ByteBuffer.allocate(4096);
		buffToSend.put(ASCII.encode(headerPostString));
		buffToSend.putLong(serverAnswer.getJobId());
		buffToSend.putInt(serverAnswer.getTaskNumber());
		try {
			buffToSend.put(UTF8.encode(contentToSend.toString()));
		} catch (BufferOverflowException e) {
			contentToSend = new StringBuilder(contentWithoutLastLine).append(" \"Error\" : \"Too Long\"}").toString();
			headerPostString = createHeaderToPost(host, Long.BYTES + Integer.BYTES + contentToSend.length());

			buffToSend.clear();
			buffToSend.put(ASCII.encode(headerPostString));
			buffToSend.putLong(serverAnswer.getJobId());
			buffToSend.putInt(serverAnswer.getTaskNumber());
			buffToSend.put(UTF8.encode(contentToSend.toString()));
		}
		return buffToSend;
	}

	/**			Check if the server's answer contain an instruction to wait and come back after a number of seconds
	 * 			Returns the number of second to wait.
	 * @param sa  ServerAnswer that contains all the informations of the answer of the Server
	 * @return the number of second the client need to wait before asking again to the server a task
	 */
	private static int checkWait(ServerAnswer sa) {
		Object nbseconds = sa.map.get("ComeBackInSeconds");
		if (nbseconds != null) {
			return Integer.valueOf((String) nbseconds);
		}
		return 0;
	}

	/**	Check if the json parameter is valid or not
	 * @param json	JSON string to test 
	 * @return	true if the Json is valid. false if the Json is invalid
	 * @throws IOException
	 */
	private static boolean isInvalidJson(String json) throws IOException {
		JsonNode node;
		try {
			node = mapper.readTree(json);
		} catch (JsonProcessingException e) {
			return true;
		}
		return false;
	}

	/**	Check if the JSON parameter is compose of other JSON objects
	 * @param json	The JSON string to test
	 * @return	boolean that indicates if the JSON String is nested or not
	 * @throws JsonProcessingException
	 * @throws IOException
	 */
	private static boolean isNested(String json) throws JsonProcessingException, IOException {
		Iterator<JsonNode> it = mapper.readTree(json).iterator();
		for (JsonNode node : it.next()) {
			if (node.isObject()) {
				return true;
			}
		}
		return false;
	}

	/**	return a POST header containing the CONTENT-LENGTH of the request.
	 * @param adressServer		The address of the server.
	 * @param contentLength		
	 * @return	The POST HEADER as a string
	 */
	static String createHeaderToPost(String adressServer, int contentLength) {
		StringBuilder sb = new StringBuilder();

		sb.append("POST Answer HTTP/1.1\r\n").append("Host: ").append(adressServer).append("\r\n")
				.append("Content-Type: application/json\r\n").append("Content-Length: ").append(contentLength)
				.append("\r\n").append("\r\n");

		return sb.toString();
	}

	/**	return the content of the POST request without the last line (see the  computeLastLine method)
	 * @param sa
	 * @return
	 */
	static String createContentWithoutLastLine(ServerAnswer sa) {
		StringBuilder contentWithoutLastLine = new StringBuilder();
		contentWithoutLastLine.append("{\"JobId\": \"").append(sa.getJobId()).append("\", \"WorkerVersion\": \"")
				.append(sa.getWorkerVersion()).append("\", \"WorkerURL\": \"").append(sa.getWorkerURL())
				.append("\", \"WorkerClassName\": \"").append(sa.getWorkerClassName()).append("\", \"Task\": \"")
				.append(sa.getTaskNumber()).append("\", \"ClientId\": \"").append(sa.getClientId()).append("\",");
		return contentWithoutLastLine.toString();
	}

	/** Return a new instance of Worker for the jobId and workerVersion parameters or an already existing instance stored in the workers map
	 * @param jobId		the jobId
	 * @param workerVersion		The workerVersion for the worker associated to this JobId
	 * @param serverAnswer		serverAnswer contains all the information of the answer from the server.
	 * @return	an instance that implements the Worker interface.
	 */
	private Worker returnAWorker(Long jobId, String workerVersion, ServerAnswer serverAnswer) {

		return workers.computeIfAbsent(jobId, __ -> new HashMap<String, Worker>()).computeIfAbsent(workerVersion,
				__ -> {
					try {
						System.out.println("On connait pas ce worker. On demande une nouvelle creation.");
						return WorkerFactory.getWorker(serverAnswer.getWorkerURL(), serverAnswer.getWorkerClassName());
					} catch (Exception e) {
						throw new IllegalArgumentException();
					}
				}

		);

	}

	
	
	public static void main(String[] args) throws IOException, InterruptedException {
		String host = "ns3001004.ip-5-196-73.eu";
		int port = 8080;
//		String host = "localhost";
//		int port = 7777;

		SocketChannel sc = null;
		try {
			sc = SocketChannel.open(new InetSocketAddress(host, port));
		} catch (IOException e) {
			System.err.println("La connexion a echouï¿½");
			e.printStackTrace();
		}
		JarRetClient jarRetClient = new JarRetClient();
		int i = 0;

		/*-------BOUCLE PRINCIPALE--------*/
		while (true) {

			String GETRequest = createGetRequest(sc.getRemoteAddress());
			sc.write(ASCII.encode(GETRequest));

			HTTPReader reader = HTTPReader.useBlockingReader(sc, ByteBuffer.allocate(50));
			HTTPHeader getHeader = reader.readHeader();

			ByteBuffer contentJson = reader.readBytes(getHeader.getContentLength());
			contentJson.flip();
			
			/*We transform the Json from the server in a HashMap in ServerAnswer*/
			ServerAnswer serverAnswer = new ServerAnswer(
					mapper.readValue(getHeader.getCharset().decode(contentJson).toString(), HashMap.class));
			serverAnswer.putClientId("NEIL");

			/*Gestion de l'attente*/
			int waitSeconds = 0;
			if ((waitSeconds = checkWait(serverAnswer)) != 0) {
				System.out.println("ON DOIT ATTENDRE");
				Thread.sleep(waitSeconds * 1000);
				continue;
			}
			System.out.println("on demande une instance");
			Worker worker = jarRetClient.returnAWorker(serverAnswer.getJobId(), serverAnswer.getWorkerVersion(),
					serverAnswer);
			System.out.println("L'instance est crée.");
			String contentWithoutLastLine = createContentWithoutLastLine(serverAnswer);
			String lastLine = computeLastLine(worker, serverAnswer.getTaskNumber());
			String contentToSend = new StringBuilder(contentWithoutLastLine).append(lastLine).toString();

			ByteBuffer buffToSend = createBufferToSend(host, serverAnswer, contentWithoutLastLine, contentToSend);
			buffToSend.flip();
			sc.write(buffToSend);

			readServerAnswerAfterPost(sc);
			i++; 
		}

//		sc.close();

	}

}
