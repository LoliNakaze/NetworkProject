package upem.jarret.server;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by nakaze on 17/04/17.
 */

public class JobMonitor {
    static class Job {
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
        static List<Job> joblistFromFile(Path path) throws IOException {
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

    private BitSet bitSet;
    private Job job;
    private Integer nextId = -1;
    private final Path path;
    private static int jobPrioritySum = 0;

    JobMonitor(Job job) {
        this.job = job;
        jobPrioritySum += Integer.parseInt(job.getJobPriority());
        path = Paths.get(job.getWorkerClassName() + "answers.txt");
        bitSet = new BitSet(Integer.parseInt(job.getJobTaskNumber()));
    }

    public int getJobPriority() {
        return Integer.parseInt(job.getJobPriority());
    }

    public boolean isComplete() {
        return Integer.parseInt(job.getJobTaskNumber()) == bitSet.cardinality();
    }

    private int getNextTask() {
        nextId = bitSet.nextClearBit(++nextId);
        return nextId;
    }

    public static List<JobMonitor> jobMonitorListFromFile(Path path) throws IOException {
        return Job.joblistFromFile(path).stream().map(JobMonitor::new).collect(Collectors.toList());
    }

    public String sendTask() {
        return "{"
                + "\"JobId\":\"" + job.getJobId() + "\","
                + "\"WorkerVersion\":\"" + job.getWorkerVersionNumber() + "\","
                + "\"WorkerURL\":\"" + job.getWorkerURL() + "\","
                + "\"WorkerClassName\":\"" + job.getWorkerClassName() + "\","
                + "\"Task\":\"" + getNextTask() + "\"}";
    }

    public void updateATask(int task, String response) throws IOException {
        if (bitSet.get(task))
            return;

        bitSet.set(task);
        try (OutputStream out = Files.newOutputStream(path, StandardOpenOption.APPEND, StandardOpenOption.CREATE);
             BufferedWriter reader =
                     new BufferedWriter(new OutputStreamWriter(out))) {
            out.write((task + ":" + response).getBytes());
        }
    }
}