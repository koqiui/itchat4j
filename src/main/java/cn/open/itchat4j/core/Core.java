package cn.open.itchat4j.core;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.http.client.CookieStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.annotation.JSONField;

import cn.open.itchat4j.WechatHelper;
import cn.open.itchat4j.beans.BaseMsg;
import cn.open.itchat4j.enums.params.BaseParamEnum;
import cn.open.itchat4j.utils.MyHttpClient;

/**
 * 核心存储类，全局只保存一份，单例模式
 * 
 * @author https://github.com/yaphone
 * @date 创建时间：2017年4月23日 下午2:33:56
 * @version 1.0
 *
 */
public class Core implements Serializable, CookieStoreHolder {
	private static final long serialVersionUID = 1L;
	//
	private static Logger logger = LoggerFactory.getLogger(Core.class);

	private static Core instance = new Core();

	public static Core getInstance() {
		return instance;
	}

	//
	private CoreDataStore dataStore = new MemDataStore();

	private void initDataStore() {
		Map<String, Object> initValues = new HashMap<String, Object>() {
			private static final long serialVersionUID = 1L;
			//
			{
				this.put("useHotReload", false);
				this.put("receivingRetryCount", Integer.valueOf(5));
				this.put("lastNormalRetCodeTime", Long.valueOf(0));// 最后一次收到正常retcode的时间，秒为单位
				//
				this.put("isAlive", false);
				this.put("loginInfo", new HashMap<String, Object>(0));
				//
				this.put("memberCount", Integer.valueOf(0));
				this.put("memberIdList", new ArrayList<String>(0));// 好友+群聊+公众号+特殊账号 id列表
				//
				this.put("contactIdList", new ArrayList<String>(0));// 好友id列表
				this.put("groupIdList", new ArrayList<String>(0));// 群聊id列表
				//
				this.put("publicUserIdList", new ArrayList<String>(0));// 公众号／服务号 id列表
				this.put("specialUserIdList", new ArrayList<JSONObject>(0));// 特殊账号 id列表
			}
		};
		//
		this.dataStore.init(initValues);
	}

	private Core() {
		//
	}

	@Override
	public void saveCookieStore(CookieStore cookieStore) {
		this.dataStore.set("cookieStore", cookieStore);
	}

	@Override
	public CookieStore getCookieStore() {
		return this.dataStore.get("cookieStore");
	}

	private boolean loadStoreData() {
		return this.dataStore.load();
	}

	@JSONField(serialize = false)
	private transient ReentrantLock myHttpClientLock = new ReentrantLock();
	@JSONField(serialize = false)
	private transient MyHttpClient myHttpClient;

	private transient boolean initialized = false;

	public void doInit() {
		this.doInit(null);
	}

	public void doInit(CoreDataStore dataStore) {
		if (!initialized) {
			initialized = true;
			//
			if (dataStore != null) {
				this.dataStore = dataStore;
			}
			//
			this.initDataStore();
			//
			this.loadStoreData();
			//
			try {
				myHttpClientLock.lock();
				//
				this.myHttpClient = new MyHttpClient(this);
			} finally {
				myHttpClientLock.unlock();
			}
			// 重置为当前时间
			this.setAlive(false);
			//
			logger.info("Core.doInit OK .");
		} else {
			logger.warn("Core.doInit Skipped.");
		}
	}

	public CoreDataStore getDataStore() {
		return this.dataStore;
	}

	public boolean saveStoreData() {
		return this.dataStore.save();
	}

	// TODO 是否能正常反序列化 ??
	@JSONField(serialize = false)
	private transient Queue<BaseMsg> msgList = new ConcurrentLinkedQueue<BaseMsg>();

	/**
	 * 请求参数
	 */
	public Map<String, Object> newParamMap() {
		return new HashMap<String, Object>(1) {
			private static final long serialVersionUID = 1L;
			//
			{
				Map<String, String> map = new HashMap<String, String>();
				for (BaseParamEnum baseRequest : BaseParamEnum.values()) {
					map.put(baseRequest.param(), getLoginInfo().get(baseRequest.value()).toString());
				}
				//
				this.put("BaseRequest", map);
			}
		};
	}

	public boolean isUseHotReload() {
		return dataStore.get("useHotReload");
	}

	public void setUseHotReload(boolean useHotReload) {
		dataStore.set("useHotReload", useHotReload);
	}

	public String getHotReloadDir() {
		return dataStore.get("hotReloadDir");
	}

	public void setHotReloadDir(String hotReloadDir) {
		dataStore.set("hotReloadDir", hotReloadDir);
	}

	public int getReceivingRetryCount() {
		return dataStore.get("receivingRetryCount");
	}

	public void setReceivingRetryCount(int receivingRetryCount) {
		dataStore.set("receivingRetryCount", receivingRetryCount);
	}

	public synchronized long getLastNormalRetCodeTime() {
		return dataStore.get("lastNormalRetCodeTime");
	}

	public synchronized void setLastNormalRetCodeTime(long lastNormalRetCodeTime) {
		dataStore.set("lastNormalRetCodeTime", lastNormalRetCodeTime);
	}

	public MyHttpClient getMyHttpClient() {
		if (myHttpClient == null) {
			throw new IllegalStateException("确保先调用了 doInit(...)方法");
		}
		return myHttpClient;
	}

	public boolean isAlive() {
		return dataStore.get("isAlive");
	}

	public void setAlive(boolean alive) {
		dataStore.set("isAlive", alive);
	}

	public String getUuid() {
		return dataStore.get("uuid");
	}

	public void setUuid(String uuid) {
		dataStore.set("uuid", uuid);
	}

	public Map<String, Object> getLoginInfo() {
		return dataStore.get("loginInfo");
	}

	public void setLoginInfo(Map<String, Object> loginInfo) {
		dataStore.set("loginInfo", loginInfo);
	}

	public String getIndexUrl() {
		return dataStore.get("indexUrl");
	}

	public void setIndexUrl(String indexUrl) {
		dataStore.set("indexUrl", indexUrl);
	}

	public String getUserName() {
		return dataStore.get("userName");
	}

	public void setUserName(String userName) {
		dataStore.set("userName", userName);
	}

	public String getNickName() {
		return dataStore.get("nickName");
	}

	public void setNickName(String nickName) {
		dataStore.set("nickName", nickName);
	}

	public JSONObject getUserSelf() {
		return dataStore.get("userSelf");
	}

	public void setUserSelf(JSONObject userSelf) {
		dataStore.set("userSelf", userSelf);
	}

	public int getMemberCount() {
		return dataStore.get("memberCount");
	}

	public void setMemberCount(int memberCount) {
		dataStore.set("memberCount", memberCount);
	}

	public void setMember(String id, JSONObject member) {
		dataStore.set("member" + id, member);
		//
		if (!this.getMemberIdList().contains(id)) {
			this.getMemberIdList().add(id);
		}
	}

	public JSONObject getMember(String id) {
		return dataStore.get("member" + id);
	}

	public void setNickName(String id, String nickName) {
		dataStore.set("nickName" + id, nickName);
	}

	public String getNickName(String id) {
		return dataStore.get("nickName" + id);
	}

	/** 好友+群聊+公众号+特殊账号 id列表 */
	public List<String> getMemberIdList() {
		return dataStore.get("memberIdList");
	}

	public void setMemberIdList(List<String> memberIdList) {
		dataStore.set("memberIdList", memberIdList);
	}

	/** 好友id列表 */
	public List<String> getContactIdList() {
		return dataStore.get("contactIdList");
	}

	public void setContactIdList(List<String> contactIdList) {
		dataStore.set("contactIdList", contactIdList);
	}

	public List<JSONObject> getContactList() {
		List<String> idList = this.getContactIdList();
		List<JSONObject> retList = new ArrayList<>(idList.size());
		for (String id : idList) {
			retList.add(this.getMember(id));
		}
		return retList;
	}

	/** 群聊id列表 */
	public List<String> getGroupIdList() {
		return dataStore.get("groupIdList");
	}

	public void setGroupIdList(List<String> groupIdList) {
		dataStore.set("groupIdList", groupIdList);
	}

	public List<JSONObject> getGroupList() {
		List<String> idList = this.getGroupIdList();
		List<JSONObject> retList = new ArrayList<>(idList.size());
		for (String id : idList) {
			retList.add(this.getMember(id));
		}
		return retList;
	}

	/** 公众号／服务号 id列表 */
	public List<String> getPublicUserIdList() {
		return dataStore.get("publicUserIdList");
	}

	public void setPublicUserIdList(List<String> publicUserIdList) {
		dataStore.set("publicUserIdList", publicUserIdList);
	}

	public List<JSONObject> getPublicUserList() {
		List<String> idList = this.getPublicUserIdList();
		List<JSONObject> retList = new ArrayList<>(idList.size());
		for (String id : idList) {
			retList.add(this.getMember(id));
		}
		return retList;
	}

	/** 特殊账号 id列表 */
	public List<String> getSpecialUserIdList() {
		return dataStore.get("specialUserIdList");
	}

	public void setSpecialUserIdList(List<String> specialUserIdList) {
		dataStore.set("specialUserIdList", specialUserIdList);
	}

	public List<JSONObject> getSpecialUserList() {
		List<String> idList = this.getSpecialUserIdList();
		List<JSONObject> retList = new ArrayList<>(idList.size());
		for (String id : idList) {
			retList.add(this.getMember(id));
		}
		return retList;
	}

	public Queue<BaseMsg> getMsgList() {
		return msgList;
	}

	public void setMsgList(Queue<BaseMsg> msgList) {
		this.msgList = msgList;
	}

}
