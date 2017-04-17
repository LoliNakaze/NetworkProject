package upem.jarret.server;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    public static List<Job> joblistFromFile(Path path) throws IOException {
        // TODO : I don't think it handles multiple jobs in the file
        ArrayList<Job> jobs = new ArrayList<>();

        try (Stream<String> lines = Files.lines(path)) {
            return Arrays.stream(lines.collect(Collectors.joining()).split("}"))
                    .map(s -> {
                        try {
                            return new ObjectMapper().readValue(s + "}", Job.class);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }).collect(Collectors.toList());
        }
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

    @Override
    public String toString() {
        return "{\n"
                + "\tJobId : " + JobId + "\n"
                + "\tJobTaskNumber : " + JobTaskNumber + "\n"
                + "\tJobDescription : " + JobDescription + "\n"
                + "\tJobPriority : " + JobPriority + "\n"
                + "\tWorkerVersionNumber : " + WorkerVersionNumber + "\n"
                + "\tWorkerURL : " + WorkerURL + "\n"
                + "\tWorkerClassName : " + WorkerClassName + "\n}";
    }

    public static void main(String[] args) throws IOException {
        List<Job> jobs = Job.joblistFromFile(Paths.get("resources/JarRetJobs.json"));
        jobs.forEach(Job::toString);
    }
}