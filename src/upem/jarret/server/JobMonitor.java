package upem.jarret.server;

import java.util.BitSet;

/**
 * Created by nakaze on 17/04/17.
 */

public class JobMonitor {
    private BitSet bitSet;
    private Job job;
    private Integer nextId = -1;

    JobMonitor(Job job) {
        this.job = job;
        bitSet = new BitSet(Integer.parseInt(job.getJobTaskNumber()));
    }

    public int getJobPriority() {
        return Integer.parseInt(job.getJobPriority());
    }

    private int getNextTask() {
        nextId = bitSet.nextClearBit(++nextId);
        return nextId;
    }

    public String sendTask() {
        return "{"
                + "\"JobId\":\"" + job.getJobId() + "\","
                + "\"WorkerVersion\":\"" + job.getWorkerVersionNumber() + "\","
                + "\"WorkerURL\":\"" + job.getWorkerURL() + "\","
                + "\"WorkerClassName\":\"" + job.getWorkerClassName() + "\","
                + "\"Task\":\"" + getNextTask() + "\"}";
    }

    public void updateATask(int task) {
        bitSet.set(task);
    }
}