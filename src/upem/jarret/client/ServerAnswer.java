package upem.jarret.client;

import java.util.HashMap;

public class ServerAnswer {

	public ServerAnswer(HashMap<String, Object> map) {
		this.map = map;
	}

	HashMap<String, Object> map;

	public long getJobId() {
		return Long.valueOf( (String) map.get("JobId") ) ;
	}

	public String getWorkerVersion() {
		return (String) map.get("WorkerVersion");
	}

	public String getWorkerURL() {
		return (String) map.get("WorkerURL");
	}

	public String getWorkerClassName() {
		return (String) map.get("WorkerClassName");
	}

	public Integer getTaskNumber() {
		return Integer.valueOf( (String)map.get("Task"));
	}

	public String getClientId() {
		return (String) map.get("Task");
	}

	public String getComputeAnswerJson() {
		return (String) map.get("Answer");
	}

	public void putClientId(String clientId) {
		map.put("ClientId", clientId);
	}

	public void putComputeAnswerJson(String answerJson) {
		map.put("Answer", answerJson);
	}
}
