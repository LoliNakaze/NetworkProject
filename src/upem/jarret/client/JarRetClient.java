package upem.jarret.client;

import upem.jarret.worker.Worker;
import upem.jarret.worker.WorkerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;

/**
 * Created by nakaze on 10/04/17.
 */
public class JarRetClient {
    private final HTTPClient client;

    JarRetClient (String address, int port) throws IOException {
        this.client = new HTTPClient(address, port,
                "GET Task HTTP/1.1\r\n" +
                "Host: " + address + "\r\n" +
                "\r\n");
    }

    void launch() throws IOException, IllegalAccessException, InstantiationException, ClassNotFoundException, InterruptedException {
        while (!Thread.interrupted()) {
            String serverResponse = client.get();
            // TODO Jackson : decode the server response
            String comeback = null; // ComebackInSeconds

            if (comeback != null) {
                Thread.sleep(Integer.parseInt(comeback));
                continue;
            }

            String jarURL = null;
            String className = null;
            int task = -1;
            // TODO Get JobID : if the worker and the jobID are equal → do not create another Worker

            Worker worker = WorkerFactory.getWorker(jarURL, className);
            String taskResponse = worker.compute(task);
            // TODO Jackson : encode the client response ??? Maybe it's already encoded

            client.write(taskResponse);
        }
    }
}
