package cn.open.itchat4j.core;

/**
 * 用户状态及相关信息事件接口
 * 
 * @author koqiui
 * @date 2019年4月1日 下午12:27:26
 *
 */
public interface CoreStateListener {
	/** 用户已上线 */
	void onUserOnline(String nodeName);

	/** 用户相关信息已就绪（最近联系人已获取完毕） */
	void onUserReady(String nodeName);

	/** 用户已下线 */
	void onUserOffline(String nodeName);

	/** 用户状态及相关信息已变更 */
	void onDataChanged(String nodeName, long dataVersion);

	/** 用户登陆的uuid已刷新 */
	void onUuidRefreshed(String nodeName, String uuid);

	/** 是否正在等着用户扫码 */
	void onWaitForScan(String nodeName, boolean waiting);

	/** 登陆失败（通常是获取不到登陆用的uuid） */
	void onLoginFail(String nodeName, String message);

}
