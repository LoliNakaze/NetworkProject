package upem.jarret.server;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.stream.Stream;

/**
 * Created by nakaze on 17/04/17.
 */
public class Job {
    private String JobId;
    private String JobTaskNumber;
    private String JobDescription;
    private String JobPriority;
    private String WorkerVersionNumber;
    private String WorkerURL;
    private String WorkerClassName;

    private Job() {
    }

    /**
     * @param path The path to the file which contains the jobs for the server.
     * @return The list of jobs given in the file pointed by path.
     * @throws IOException
     */
    public Job[] jobFromFile(Path path) throws IOException {
        // TODO : I don't think it handles multiple jobs in the file
        InputStream inJson = Files.newInputStream(path, StandardOpenOption.READ);

        return new ObjectMapper().readValue(inJson, Job[].class);
    }

    public String getJobPriority() {
        return JobPriority;
    }

    public void setJobPriority(String jobPriority) {
        JobPriority = jobPriority;
    }

    public String getWorkerVersionNumber() {
        return WorkerVersionNumber;
    }

    public void setWorkerVersionNumber(String workerVersionNumber) {
        WorkerVersionNumber = workerVersionNumber;
    }

    public String getWorkerURL() {
        return WorkerURL;
    }

    public void setWorkerURL(String workerURL) {
        WorkerURL = workerURL;
    }

    public String getWorkerClassName() {
        return WorkerClassName;
    }

    public void setWorkerClassName(String workerClassName) {
        WorkerClassName = workerClassName;
    }

    public String getJobDescription() {
        return JobDescription;
    }

    public void setJobDescription(String jobDescription) {
        JobDescription = jobDescription;
    }

    public String getJobTaskNumber() {
        return JobTaskNumber;
    }

    public void setJobTaskNumber(String jobTaskNumber) {
        JobTaskNumber = jobTaskNumber;
    }

    public String getJobId() {
        return JobId;
    }

    public void setJobId(String jobId) {
        JobId = jobId;
    }
}
