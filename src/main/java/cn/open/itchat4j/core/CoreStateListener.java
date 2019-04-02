package cn.open.itchat4j.core;

public interface CoreStateListener {
	void onUserOnline(String nodeName);
	
	void onUserReady(String nodeName);

	void onUserOffline(String nodeName);

	void onDataChanged(String nodeName, long dataVersion);

	void onUuidRefreshed(String nodeName, String uuid);

	void onWaitForScan(String nodeName, boolean waiting);
}
