package cn.open.itchat4j.core;

public interface CoreStateListener {
	void onUserOnline(String nodeName);

	void onUserOffline(String nodeName);

	void onDataChanged(long dataVersion);

	void onUuidRefreshed();
}
