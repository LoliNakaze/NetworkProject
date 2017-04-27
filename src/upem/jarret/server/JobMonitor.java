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

class JobMonitor implements Closeable {
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
    private final OutputStream out;
    private final BufferedWriter writer;

    JobMonitor(Job job, String answerPath) throws IOException {
        this.job = job;
        jobPrioritySum += Integer.parseInt(job.getJobPriority());
        path = Paths.get(answerPath + job.getWorkerClassName() + "answers.txt");
        bitSet = new BitSet(Integer.parseInt(job.getJobTaskNumber()));
        out = Files.newOutputStream(path, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
        writer = new BufferedWriter(new OutputStreamWriter(out));
    }

    int getJobPriority() {
        return Integer.parseInt(job.getJobPriority());
    }

    static int getPrioritySum() {
        return jobPrioritySum;
    }

    boolean isComplete() {
        return Integer.parseInt(job.getJobTaskNumber()) == bitSet.cardinality();
    }

    private int getNextTask() {
        nextId = bitSet.nextClearBit(++nextId);
        return nextId;
    }

    static List<JobMonitor> jobMonitorListFromFile(Path path, String answerPath) throws IOException {
        return Job.joblistFromFile(path).stream().map(j -> {
            try {
                return new JobMonitor(j, answerPath);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }).collect(Collectors.toList());
    }

    String sendTask() {
        return "{"
                + "\"JobId\":\"" + job.getJobId() + "\","
                + "\"WorkerVersion\":\"" + job.getWorkerVersionNumber() + "\","
                + "\"WorkerURL\":\"" + job.getWorkerURL() + "\","
                + "\"WorkerClassName\":\"" + job.getWorkerClassName() + "\","
                + "\"Task\":\"" + getNextTask() + "\"}";
    }

    void updateATask(int task, String response) throws IOException {
        if (bitSet.get(task))
            return;

        bitSet.set(task);
        out.write((task + ":" + response + "\r\n").getBytes());
    }

    @Override
    public void close() throws IOException {
        out.close();
        writer.close();
    }
}