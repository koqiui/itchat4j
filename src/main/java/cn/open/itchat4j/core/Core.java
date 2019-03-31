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

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.annotation.JSONField;

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

	private Map<String, Object> getInitDataStoreValues() {
		return new HashMap<String, Object>() {
			private static final long serialVersionUID = 1L;
			//
			{
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
				this.put("specialUserIdList", new ArrayList<String>(0));// 特殊账号 id列表
			}
		};
	}

	private void initDataStore() {
		this.dataStore.init(this.getInitDataStoreValues());
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

	public boolean saveStoreData() {
		this.dataStore.set("dataVersionTs", System.currentTimeMillis());
		return this.dataStore.save();
	}

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
					Object value = getLoginInfo().get(baseRequest.value());
					String valueStr = value == null ? null : value.toString();
					map.put(baseRequest.param(), valueStr);
				}
				//
				this.put("BaseRequest", map);
			}
		};
	}

	// 成员变更时间戳
	private transient long lastDataChngTs = System.currentTimeMillis();
	private transient long lastDataSaveTs = lastDataChngTs;

	/**
	 * 数据是否有变动（以便定时持久化）
	 * 
	 * @return
	 */
	public boolean hasDataChanges() {
		return this.lastDataChngTs > this.lastDataSaveTs;
	}

	/** 刷新数据变动保存时间 */
	public void setDataSavedTs() {
		this.lastDataSaveTs = System.currentTimeMillis();
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

	public void reset() {
		this.msgList.clear();
		//
		dataStore.clear();
		dataStore.save();
		//
		this.initialized = false;
		this.doInit();
	}

	/**
	 * 清除所有联系人信息和消息列表
	 * 
	 * @author koqiui
	 * @date 2019年3月31日 下午7:52:08
	 *
	 */
	@SuppressWarnings("unchecked")
	public void clearAllContactsAndMsgs() {
		List<String> allMemberIds = this.getMemberIdList();
		List<String> groupIdList = this.getGroupIdList();
		String memeberId = null;
		for (int i = allMemberIds.size() - 1; i >= 0; i--) {
			memeberId = allMemberIds.get(i);
			if (groupIdList.indexOf(memeberId) == -1) {
				this.dataStore.del(this.makeMemberKey(memeberId));
				this.dataStore.del(this.makeNickNameKey(memeberId));
				//
				allMemberIds.remove(i);
			}
		}
		//
		Map<String, Object> initValues = this.getInitDataStoreValues();
		this.setMemberCount(0);
		this.setMemberIdList((List<String>) initValues.get("memberIdList"));
		this.setContactIdList((List<String>) initValues.get("contactIdList"));
		// this.setGroupIdList((List<String>) initValues.get("groupIdList"));//群组不好获取
		this.setPublicUserIdList((List<String>) initValues.get("publicUserIdList"));
		this.setSpecialUserIdList((List<String>) initValues.get("specialUserIdList"));
		//
		this.msgList.clear();
	}

	public String getLastMessage() {
		return dataStore.get("lastMessage");
	}

	public void setLastMessage(String message) {
		dataStore.set("lastMessage", message);
		//
		this.lastDataChngTs = System.currentTimeMillis();
	}

	public String getUuid() {
		return dataStore.get("uuid");
	}

	public void setUuid(String uuid) {
		dataStore.set("uuid", uuid);
		//
		this.lastDataChngTs = System.currentTimeMillis();
	}

	public Map<String, Object> getLoginInfo() {
		return dataStore.get("loginInfo");
	}

	public void setLoginInfo(Map<String, Object> loginInfo) {
		dataStore.set("loginInfo", loginInfo);
		//
		this.lastDataChngTs = System.currentTimeMillis();
	}

	public String getIndexUrl() {
		return dataStore.get("indexUrl");
	}

	public void setIndexUrl(String indexUrl) {
		dataStore.set("indexUrl", indexUrl);
		//
		this.lastDataChngTs = System.currentTimeMillis();
	}

	public String getUserName() {
		return dataStore.get("userName");
	}

	public void setUserName(String userName) {
		dataStore.set("userName", userName);
		//
		this.lastDataChngTs = System.currentTimeMillis();
	}

	public String getNickName() {
		return dataStore.get("nickName");
	}

	public void setNickName(String nickName) {
		dataStore.set("nickName", nickName);
		//
		this.lastDataChngTs = System.currentTimeMillis();
	}

	public JSONObject getUserSelf() {
		return dataStore.get("userSelf");
	}

	public void setUserSelf(JSONObject userSelf) {
		dataStore.set("userSelf", userSelf);
		//
		this.lastDataChngTs = System.currentTimeMillis();
	}

	public int getMemberCount() {
		return dataStore.get("memberCount");
	}

	public void setMemberCount(int memberCount) {
		dataStore.set("memberCount", memberCount);
		//
		this.lastDataChngTs = System.currentTimeMillis();
	}

	private String makeMemberKey(String id) {
		return "member" + id;
	}

	public void setMember(String id, JSONObject member) {
		dataStore.set(this.makeMemberKey(id), member);
		//
		this.addMemberId(id);
	}

	public JSONObject getMember(String id) {
		return dataStore.get(this.makeMemberKey(id));
	}

	private String makeNickNameKey(String id) {
		return "nickName" + id;
	}

	public void setNickName(String id, String nickName) {
		dataStore.set(this.makeNickNameKey(id), nickName);
	}

	public String getNickName(String id) {
		return dataStore.get(this.makeNickNameKey(id));
	}

	/** 好友+群聊+公众号+特殊账号 id列表 */
	private List<String> getMemberIdList() {
		return dataStore.get("memberIdList");
	}

	public boolean addMemberId(String id) {
		List<String> theIdList = this.getMemberIdList();
		if (!theIdList.contains(id)) {
			theIdList.add(id);
			this.setMemberIdList(theIdList);
			return true;
		}
		return false;
	}

	public void setMemberIdList(List<String> memberIdList) {
		dataStore.set("memberIdList", memberIdList);
		//
		this.lastDataChngTs = System.currentTimeMillis();
	}

	/** 好友id列表 */
	private List<String> getContactIdList() {
		return dataStore.get("contactIdList");
	}

	public boolean addContactId(String id) {
		List<String> theIdList = this.getContactIdList();
		if (!theIdList.contains(id)) {
			theIdList.add(id);
			this.setContactIdList(theIdList);
			return true;
		}
		return false;
	}

	public void setContactIdList(List<String> contactIdList) {
		dataStore.set("contactIdList", contactIdList);
		//
		this.lastDataChngTs = System.currentTimeMillis();
	}

	public List<JSONObject> getContactList() {
		List<String> idList = this.getContactIdList();
		List<JSONObject> retList = new ArrayList<>(idList.size());
		for (String id : idList) {
			retList.add(this.getMember(id));
		}
		return retList;
	}

	private transient long lastGroupChngTs = System.currentTimeMillis();
	private transient long lastGroupSyncTs = lastGroupChngTs;

	/** 是否有没有同步的群组（因为群组比较特别只有点进去才能得到通知，才能拿到userNmae，还拿不到nickName） */
	public boolean hasNoneSyncGroups() {
		return lastGroupChngTs > lastGroupSyncTs;
	}

	/** 刷新群组数据同步时间 */
	public void setLastSyncGroupTs() {
		this.lastGroupSyncTs = System.currentTimeMillis();
	}

	/** 群聊id列表 注意不要在返回的数据上修改（不能直到数据变更） */
	@Deprecated
	public List<String> getGroupIdList() {
		return dataStore.get("groupIdList");
	}

	public boolean addGroupId(String id) {
		List<String> theIdList = this.getGroupIdList();
		if (!theIdList.contains(id)) {
			theIdList.add(id);
			this.setGroupIdList(theIdList);
			//
			this.lastGroupChngTs = System.currentTimeMillis();
			//
			return true;
		}
		return false;
	}

	public void setGroupIdList(List<String> groupIdList) {
		dataStore.set("groupIdList", groupIdList);
		//
		this.lastDataChngTs = System.currentTimeMillis();
	}

	public List<JSONObject> getGroupList() {
		List<String> idList = this.getGroupIdList();
		List<JSONObject> retList = new ArrayList<>(idList.size());
		for (String id : idList) {
			retList.add(this.getMember(id));
		}
		return retList;
	}

	/**
	 * 根据groupIdList返回群成员列表
	 * 
	 * @date 2017年6月13日 下午11:12:31
	 * @param groupId
	 * @return
	 */
	public JSONArray getMemberListByGroupId(String groupId) {
		JSONObject member = this.getMember(groupId);
		return (JSONArray) (member == null ? null : member.get("MemberList"));
	}

	/** 公众号／服务号 id列表 */
	private List<String> getPublicUserIdList() {
		return dataStore.get("publicUserIdList");
	}

	public boolean addPublicUserId(String id) {
		List<String> theIdList = this.getPublicUserIdList();
		if (!theIdList.contains(id)) {
			theIdList.add(id);
			this.setPublicUserIdList(theIdList);
			return true;
		}
		return false;
	}

	public void setPublicUserIdList(List<String> publicUserIdList) {
		dataStore.set("publicUserIdList", publicUserIdList);
		//
		this.lastDataChngTs = System.currentTimeMillis();
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
	private List<String> getSpecialUserIdList() {
		return dataStore.get("specialUserIdList");
	}

	public boolean addSpecialUserId(String id) {
		List<String> theIdList = this.getSpecialUserIdList();
		if (!theIdList.contains(id)) {
			theIdList.add(id);
			this.setSpecialUserIdList(theIdList);
			return true;
		}
		return false;
	}

	public void setSpecialUserIdList(List<String> specialUserIdList) {
		dataStore.set("specialUserIdList", specialUserIdList);
		//
		this.lastDataChngTs = System.currentTimeMillis();
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
