package cn.open.itchat4j;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;

import org.apache.http.Consts;
import org.apache.http.HttpEntity;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import cn.open.itchat4j.beans.BaseMsg;
import cn.open.itchat4j.beans.ImageBytes;
import cn.open.itchat4j.core.Core;
import cn.open.itchat4j.core.CoreDataStore;
import cn.open.itchat4j.enums.MsgTypeCodeEnum;
import cn.open.itchat4j.enums.MsgTypeValueEnum;
import cn.open.itchat4j.enums.ResultEnum;
import cn.open.itchat4j.enums.RetCodeEnum;
import cn.open.itchat4j.enums.StorageLoginInfoEnum;
import cn.open.itchat4j.enums.URLEnum;
import cn.open.itchat4j.enums.params.BaseParamEnum;
import cn.open.itchat4j.enums.params.LoginParamEnum;
import cn.open.itchat4j.enums.params.StatusNotifyParamEnum;
import cn.open.itchat4j.enums.params.UUIDParamEnum;
import cn.open.itchat4j.tools.CommonTools;
import cn.open.itchat4j.utils.Config;
import cn.open.itchat4j.utils.SleepUtils;

/**
 * 登陆服务辅助类
 * 
 * @author https://github.com/yaphone
 * @date 创建时间：2017年5月13日 上午12:09:35
 * @version 1.0
 * 
 *          TODO 重置联系人信息
 */
public class WechatHelper {
	private static Logger logger = LoggerFactory.getLogger(WechatHelper.class);

	private Core core = Core.getInstance();

	private WechatHelper() {
		System.setProperty("jsse.enableSNIExtension", "false"); // 防止SSL错误
	}

	private static WechatHelper instance = new WechatHelper();
	//
	private Map<String, List<String>> wxDomainUrlMap = new HashMap<String, List<String>>() {
		private static final long serialVersionUID = 1L;

		{
			put("wx.qq.com", new ArrayList<String>() {
				/**
				 * 
				 */
				private static final long serialVersionUID = 1L;

				{
					add("file.wx.qq.com");
					add("webpush.wx.qq.com");
				}
			});

			put("wx2.qq.com", new ArrayList<String>() {
				/**
				 * 
				 */
				private static final long serialVersionUID = 1L;

				{
					add("file.wx2.qq.com");
					add("webpush.wx2.qq.com");
				}
			});

			put("wx8.qq.com", new ArrayList<String>() {
				/**
				 * 
				 */
				private static final long serialVersionUID = 1L;

				{
					add("file.wx8.qq.com");
					add("webpush.wx8.qq.com");
				}
			});

			put("web2.wechat.com", new ArrayList<String>() {
				/**
				 * 
				 */
				private static final long serialVersionUID = 1L;

				{
					add("file.web2.wechat.com");
					add("webpush.web2.wechat.com");
				}
			});

			put("wechat.com", new ArrayList<String>() {
				/**
				 * 
				 */
				private static final long serialVersionUID = 1L;

				{
					add("file.web.wechat.com");
					add("webpush.web.wechat.com");
				}
			});
		}

	};

	public static WechatHelper getInstance() {
		return instance;
	}

	public void initCore() {
		this.initCore(null);
	}

	public void initCore(CoreDataStore dataStore) {
		core.doInit(dataStore);
		//
		this.startup();
	}

	private ReentrantLock isRunningLock = new ReentrantLock();
	private transient boolean isRunning = false;

	public void shutdown() {
		try {
			isRunningLock.lock();
			//
			if (!isRunning) {
				logger.warn("Helper不在运行中");
				return;
			}
			isRunning = false;
			//
			boolean groupSyncFlag = false;
			if (core.hasNoneSyncGroups()) {
				fetchGroups();
				core.setLastSyncGroupTs();
				groupSyncFlag = true;
			}
			if (groupSyncFlag || core.hasDataChanges()) {
				core.saveStoreData();
				core.setDataSavedTs();
			}
			//
			this.stopHeatbeat();
			this.stopDataMonitor();
		} finally {
			isRunningLock.unlock();
		}
	}

	public void startup() {
		try {
			isRunningLock.lock();
			//
			if (isRunning) {
				logger.warn("Helper正在运行中");
				return;
			}
			isRunning = true;
			//
			logger.info("Helper已启动 OK");
		} finally {
			isRunningLock.unlock();
		}
	}

	public void doLogin() {
		this.doLogin(null);
	}

	/**
	 * 如果指定图片路径则从本地打开，仅用于测试
	 * 
	 * @author koqiui
	 * @date 2019年3月31日 下午8:21:39
	 * 
	 * @param qrPath
	 */
	public void doLogin(String qrPath) {
		if (core.isAlive()) {
			logger.warn("已经登陆...");
		}
		//
		Boolean result = this.tryToLogin();
		while (result == null) {
			logger.info("等待扫码确认登陆...");
			SleepUtils.sleep(1000);
			result = this.tryToLogin();
		}
		//
		if (Boolean.TRUE.equals(result)) {
			logger.info(("登陆成功."));
		} else {
			int tryTimes = 15;
			for (int count = 0; count < tryTimes;) {
				count++;
				//
				logger.info("获取UUID");
				// 10*6秒
				String uuid = null;
				int uuidTimes = 6;
				while ((uuid = this.getUuid(true)) == null && uuidTimes > 0) {
					uuidTimes--;
					logger.warn("10秒后重新获取uuid");
					SleepUtils.sleep(10000);
				}
				//
				if (uuid == null) {
					if (count == tryTimes) {
						logger.error("获取登陆uuid失败，登陆不了");
						return;
					}
				} else {
					if (qrPath == null) {
						logger.info("请在浏览器中打开并扫码 " + this.getQrImageUrl(uuid));
						core.setLastMessage("请在浏览器中打开并扫码 ");
						break;
					} else {
						logger.info("获取登陆二维码图片");
						if (this.getAndOpenQrImage(uuid, qrPath)) {
							break;
						}
					}
				}
			}
			//
			this.doLogin(qrPath);
		}
		//

		logger.info("初始化基本信息");
		if (this.initBasicInfo()) {
			// CommonTools.clearScreen();
			logger.info(String.format("欢迎  %s", core.getNickName()));

			logger.info("清除所有联系人信息和消息列表");
			core.clearAllContactsAndMsgs();

			logger.info("开启微信状态通知");
			this.initStatusNotify();

			logger.info("获取联系人信息");
			this.fetchContacts();

			logger.info("获取群好友及群好友列表");
			this.fetchGroups();

			logger.info("保存最新数据");
			core.saveStoreData();

			core.setLastSyncGroupTs();
			core.setDataSavedTs();

			logger.info("启动心跳和消息监测");
			this.startHeatbeat();

			logger.info("启动数据变动监测");
			this.startDataMonitor();
		}

	}

	/**
	 * 是否仍然在线
	 */
	public boolean isAlive() {
		return core.isAlive();
	}

	/**
	 * 获取登陆用的uuid
	 * 
	 * @param refresh
	 *            是否刷新
	 * @return
	 */
	private String getUuid(boolean refresh) {
		String retUuid = refresh ? null : core.getUuid();
		if (retUuid == null) {
			// 组装参数和URL
			List<BasicNameValuePair> params = new ArrayList<BasicNameValuePair>();
			params.add(new BasicNameValuePair(UUIDParamEnum.APP_ID.param(), UUIDParamEnum.APP_ID.value()));
			params.add(new BasicNameValuePair(UUIDParamEnum.FUN.param(), UUIDParamEnum.FUN.value()));
			params.add(new BasicNameValuePair(UUIDParamEnum.LANG.param(), UUIDParamEnum.LANG.value()));
			params.add(new BasicNameValuePair(UUIDParamEnum._.param(), String.valueOf(System.currentTimeMillis())));

			HttpEntity entity = core.getMyHttpClient().doGet(URLEnum.UUID_URL.getUrl(), params, true, null);

			try {
				String result = EntityUtils.toString(entity);
				String regEx = "window.QRLogin.code = (\\d+); window.QRLogin.uuid = \"(\\S+?)\";";
				Matcher matcher = CommonTools.getMatcher(regEx, result);
				if (matcher.find()) {
					String retCode = matcher.group(1);
					if ((ResultEnum.SUCCESS.getCode().equals(retCode))) {
						retUuid = matcher.group(2);
						core.setUuid(retUuid);
						logger.info("已刷新了 登陆uuid");
						waitingLoginScan = true;
					}
				} else {
					String regEx2 = "window.QRLogin.code = (\\d+); window.QRLogin.error = \"(\\S*?)\";";
					matcher = CommonTools.getMatcher(regEx2, result);
					if (matcher.find()) {
						String message = null;
						String retCode = matcher.group(1);
						if (ResultEnum.WAIT_SCAN.getCode().equals(retCode)) {
							message = ResultEnum.WAIT_SCAN.getMessage();
							logger.info(message);
							retUuid = core.getUuid();
							waitingLoginScan = true;
						} else if (ResultEnum.WAIT_CONFIRM.getCode().equals(retCode)) {
							message = ResultEnum.WAIT_CONFIRM.getMessage();
							logger.info(message);
							retUuid = core.getUuid();
							waitingLoginScan = true;
						}
					}
				}
			} catch (Exception e) {
				logger.warn(e.getMessage());
			}
		} else {
			logger.info("使用现有 登陆uuid");
		}
		return retUuid;
	}

	/**
	 * 获取登陆uuid对应的二维码图片url
	 * 
	 * @param uuid
	 * @return
	 */
	private String getQrImageUrl(String uuid) {
		if (uuid == null || uuid.trim().equals("")) {
			uuid = this.getUuid(true);// 获取新的uuid
		}
		return uuid == null ? null : URLEnum.QRCODE_URL.getUrl() + uuid;
	}

	/**
	 * 获取登陆uuid对应的二维码图片url
	 * 
	 * @param refresh
	 *            是否刷新获取
	 * @return
	 */
	public String getQrImageUrl(boolean refresh) {
		return this.getQrImageUrl(refresh ? null : core.getUuid());
	}

	/**
	 * 获取登陆uuid对应的二维码字节流数据
	 */
	private ImageBytes getQrImageBytes(String uuid) {
		try {
			String qrUrl = getQrImageUrl(uuid);
			if (qrUrl == null) {
				return null;
			}
			HttpEntity entity = core.getMyHttpClient().doGet(qrUrl, null, true, null);
			ImageBytes qrImageBytes = new ImageBytes();
			qrImageBytes.data = EntityUtils.toByteArray(entity);
			if (qrImageBytes.data == null || qrImageBytes.data.length < 1) {
				logger.warn("获取的QrImage为空（无效）");
			}
			return qrImageBytes;
		} catch (Exception e) {
			logger.warn(e.getMessage());
			return null;
		}
	}

	/**
	 * 获取并打开登陆二维码（用于测试/调试扫码登陆）
	 */
	private boolean getAndOpenQrImage(String uuid, String qrPathDir) {
		ImageBytes qrImageBytes = this.getQrImageBytes(uuid);
		if (qrImageBytes == null) {
			return false;
		}
		try {
			File qrDir = new File(qrPathDir);
			if (!qrDir.exists()) {
				qrDir.mkdirs();
			}
			String qrPath = qrPathDir + File.separator + "QR.jpg";
			OutputStream out = new FileOutputStream(qrPath);
			out.write(qrImageBytes.data);
			out.flush();
			out.close();
			try {
				CommonTools.printQr(qrPath); // 打开登陆二维码图片
			} catch (Exception e) {
				logger.warn(e.getMessage());
			}
			return true;
		} catch (Exception e) {
			logger.warn(e.getMessage());
			return false;
		}
	}

	private ReentrantLock tryToLoginLock = new ReentrantLock();
	private boolean tryToLoginFlag = false;

	/** 延续性登陆uuid（只需在手机端确认即可） */
	private String getPushLoginUuid() {
		try {
			Map<String, Object> loginInfo = core.getLoginInfo();
			String url = (String) loginInfo.get("url");
			String wxuin = (String) loginInfo.get("wxuin");
			String deviceId = (String) loginInfo.get("deviceid");
			if (deviceId == null) {
				loginInfo.put("deviceid", this.createDeviceId());
			}
			if (url != null && wxuin != null) {
				url = String.format(URLEnum.WEB_WX_PUSH_LOGIN.getUrl(), url, wxuin);
				HttpEntity entity = core.getMyHttpClient().doGet(url, null, true, null);

				String result = EntityUtils.toString(entity, Consts.UTF_8);

				JSONObject resutJson = JSON.parseObject(result);
				String retCode = resutJson.getString("ret");
				RetCodeEnum retCodeEnum = RetCodeEnum.fromCode(retCode);
				if (RetCodeEnum.SUCCESS.equals(retCodeEnum)) {
					String uuid = resutJson.getString("uuid");
					core.setUuid(uuid);
					return uuid;
				} else if (RetCodeEnum.PARAM_ERROR.equals(retCodeEnum)) {
					logger.warn(RetCodeEnum.PARAM_ERROR.getMessage());
				} else if (RetCodeEnum.DEVICE_FAIL.equals(retCodeEnum)) {
					logger.warn(RetCodeEnum.DEVICE_FAIL.getMessage());
					loginInfo.put("deviceid", null);
				} else {
					logger.warn(resutJson.getString("msg"));
				}
			}
		} catch (Exception e) {
			logger.warn(e.getMessage());
		}
		//
		return null;
	}

	private boolean waitingLoginScan = false;

	/**
	 * 登陆
	 */
	private Boolean tryToLogin() {
		tryToLoginLock.lock();
		try {
			if (tryToLoginFlag) {
				return null;
			}
			tryToLoginFlag = true;
			//
			boolean isLoggedIn = false;

			int checkInterval = 1000;
			while (!isLoggedIn && isRunning) {
				try {
					if (!waitingLoginScan) {
						String uuid = this.getPushLoginUuid();
						if (uuid == null) {
							tryToLoginFlag = false;
							waitingLoginScan = false;
							return false;
						} else {
							logger.info("请在手机上点击 登陆 确认");
						}
					}
					// 组装参数和URL
					List<BasicNameValuePair> params = new ArrayList<BasicNameValuePair>();
					params.add(new BasicNameValuePair(LoginParamEnum.LOGIN_ICON.param(), LoginParamEnum.LOGIN_ICON.value()));
					params.add(new BasicNameValuePair(LoginParamEnum.UUID.param(), core.getUuid()));
					params.add(new BasicNameValuePair(LoginParamEnum.TIP.param(), LoginParamEnum.TIP.value()));
					long millis = System.currentTimeMillis();
					params.add(new BasicNameValuePair(LoginParamEnum.R.param(), String.valueOf(millis / 1579L)));
					params.add(new BasicNameValuePair(LoginParamEnum._.param(), String.valueOf(millis)));
					HttpEntity entity = core.getMyHttpClient().doGet(URLEnum.LOGIN_URL.getUrl(), params, true, null);
					String result = EntityUtils.toString(entity);
					String status = checklogin(result);
					if (ResultEnum.SUCCESS.getCode().equals(status)) {
						String errMsg = processLoginInfo(result); // 处理结果
						isLoggedIn = errMsg == null;
						if (isLoggedIn) {
							core.setAlive(isLoggedIn);
							waitingLoginScan = false;
						} else {
							logger.warn(errMsg);
						}

						break;
					} else if (ResultEnum.WAIT_SCAN.getCode().equals(status)) {
						logger.info(ResultEnum.WAIT_SCAN.getMessage());
						waitingLoginScan = true;
					} else if (ResultEnum.WAIT_CONFIRM.getCode().equals(status)) {
						logger.info(ResultEnum.WAIT_CONFIRM.getMessage());
						waitingLoginScan = true;
					} else if (ResultEnum.WAIT_TIMEOUT.getCode().equals(status)) {
						logger.info(ResultEnum.WAIT_TIMEOUT.getMessage());
						waitingLoginScan = true;
					} else {
						break;
					}
					SleepUtils.sleep(checkInterval);
				} catch (Exception e) {
					logger.warn("微信登陆异常！", e);
					break;
				}
			}
			//
			tryToLoginFlag = false;
			//
			return isLoggedIn;
		} finally {
			tryToLoginLock.unlock();
		}
	}

	/**
	 * web初始化
	 */
	private boolean initBasicInfo() {
		if (!core.isAlive()) {
			return false;
		}
		// 组装请求URL和参数
		Map<String, Object> loginInfo = core.getLoginInfo();
		String url = String.format(URLEnum.INIT_URL.getUrl(), loginInfo.get(StorageLoginInfoEnum.url.getKey()), String.valueOf(System.currentTimeMillis() / 3158L), loginInfo.get(StorageLoginInfoEnum.pass_ticket.getKey()));

		Map<String, Object> paramMap = core.newParamMap();

		// 请求初始化接口
		HttpEntity entity = core.getMyHttpClient().doPost(url, JSON.toJSONString(paramMap));
		try {
			String result = EntityUtils.toString(entity, Consts.UTF_8);
			logger.info("------ initBasicInfo ------");
			// logger.info(result);
			//
			JSONObject obj = JSON.parseObject(result);
			JSONObject baseResponse = obj.getJSONObject("BaseResponse");
			if (baseResponse != null) {
				String retCode = baseResponse.getString("Ret");
				RetCodeEnum retCodeEnum = RetCodeEnum.fromCode(retCode);
				if (RetCodeEnum.LOGIN_OTHERWHERE.equals(retCodeEnum)) {
					logger.warn(RetCodeEnum.LOGIN_OTHERWHERE.getMessage());
					//
					core.setAlive(false);
					return false;
				}
			}
			JSONObject user = obj.getJSONObject(StorageLoginInfoEnum.User.getKey());
			if (user != null) {
				JSONObject syncKey = obj.getJSONObject(StorageLoginInfoEnum.SyncKey.getKey());
				loginInfo.put(StorageLoginInfoEnum.InviteStartCount.getKey(), obj.getInteger(StorageLoginInfoEnum.InviteStartCount.getKey()));
				loginInfo.put(StorageLoginInfoEnum.SyncKey.getKey(), syncKey);

				JSONArray syncArray = syncKey.getJSONArray("List");
				StringBuilder sb = new StringBuilder();
				for (int i = 0; i < syncArray.size(); i++) {
					sb.append(syncArray.getJSONObject(i).getString("Key") + "_" + syncArray.getJSONObject(i).getString("Val") + "|");
				}
				// 1_661706053|2_661706420|3_661706415|1000_1494151022|
				String synckey = sb.toString();

				// 1_661706053|2_661706420|3_661706415|1000_1494151022
				loginInfo.put(StorageLoginInfoEnum.synckey.getKey(), synckey.substring(0, synckey.length() - 1));// 1_656161336|2_656161626|3_656161313|11_656159955|13_656120033|201_1492273724|1000_1492265953|1001_1492250432|1004_1491805192
				core.setUserName(user.getString("UserName"));
				core.setNickName(user.getString("NickName"));
				core.setUserSelf(obj.getJSONObject("User"));
				//
				return true;
			} else {
				logger.warn("初始化出错");
				return false;
			}
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * 微信状态通知
	 */
	private boolean initStatusNotify() {
		// 组装请求URL和参数
		Map<String, Object> loginInfo = core.getLoginInfo();
		String url = String.format(URLEnum.STATUS_NOTIFY_URL.getUrl(), loginInfo.get(StorageLoginInfoEnum.pass_ticket.getKey()));

		Map<String, Object> paramMap = core.newParamMap();
		paramMap.put(StatusNotifyParamEnum.CODE.param(), StatusNotifyParamEnum.CODE.value());
		paramMap.put(StatusNotifyParamEnum.FROM_USERNAME.param(), core.getUserName());
		paramMap.put(StatusNotifyParamEnum.TO_USERNAME.param(), core.getUserName());
		paramMap.put(StatusNotifyParamEnum.CLIENT_MSG_ID.param(), System.currentTimeMillis());
		String paramStr = JSON.toJSONString(paramMap);

		try {
			HttpEntity entity = core.getMyHttpClient().doPost(url, paramStr);
			@SuppressWarnings("unused")
			String result = EntityUtils.toString(entity, Consts.UTF_8);
			// logger.info("------ wxStatusNotify ------");
			// logger.info(result);
			//
			return true;
		} catch (Exception e) {
			logger.warn("微信状态通知接口失败！", e);
			return false;
		}
	}

	private void addWxMember(JSONObject member) {
		String userName = member.getString("UserName");
		String nickName = member.getString("NickName");
		core.setMember(userName, member);
		core.setNickName(userName, nickName);
		if ((member.getInteger("VerifyFlag") & 8) != 0) { // 公众号/服务号
			core.addPublicUserId(userName);
		} else if (Config.API_SPECIAL_USER.contains(userName)) { // 特殊账号
			core.addSpecialUserId(userName);
		} else if (userName.indexOf("@@") != -1) { // 群聊
			core.addGroupId(userName);
		} else if (userName.equals(core.getUserName())) { // 自己
			//
		} else { // 普通联系人
			core.addContactId(userName);
		}
	}

	/**
	 * 接收消息，放入队列
	 * 
	 * @author https://github.com/yaphone
	 * @date 2017年4月23日 下午2:30:48
	 * @param msgList
	 * @return
	 */
	private JSONArray filterWxMsg(JSONArray msgList) {
		JSONArray retMsgList = new JSONArray();
		for (int i = 0; i < msgList.size(); i++) {
			JSONObject tmpMsg = new JSONObject();
			JSONObject retMsg = msgList.getJSONObject(i);
			retMsg.put("groupMsg", false);// 是否是群消息
			String fromUserName = retMsg.getString("FromUserName");
			String toUserName = retMsg.getString("ToUserName");
			//
			if (fromUserName.contains("@@") || toUserName.contains("@@")) { // 群聊消息
				if (fromUserName.contains("@@")) {
					core.addGroupId(fromUserName);
				} else if (toUserName.contains("@@")) {
					core.addGroupId(toUserName);
				}
				// 群消息与普通消息不同的是在其消息体（Content）中会包含发送者id及":<br/>"消息，这里需要处理一下，去掉多余信息，只保留消息内容
				if (retMsg.getString("Content").contains("<br/>")) {
					String content = retMsg.getString("Content").substring(retMsg.getString("Content").indexOf("<br/>") + 5);
					retMsg.put("Content", content);
					retMsg.put("groupMsg", true);
				}
			} else {
				CommonTools.msgFormatter(retMsg, "Content");
			}
			//
			Integer tmpMsgType = retMsg.getInteger("MsgType");
			if (tmpMsgType.equals(MsgTypeValueEnum.MSGTYPE_TEXT.getValue())) { // words
				// 文本消息
				if (retMsg.getString("Url").length() != 0) {
					String regEx = "(.+?\\(.+?\\))";
					Matcher matcher = CommonTools.getMatcher(regEx, retMsg.getString("Content"));
					String data = "Map";
					if (matcher.find()) {
						data = matcher.group(1);
					}
					tmpMsg.put("Type", MsgTypeCodeEnum.MAP.getCode());
					tmpMsg.put("Text", data);
				} else {
					tmpMsg.put("Type", MsgTypeCodeEnum.TEXT.getCode());
					tmpMsg.put("Text", retMsg.getString("Content"));
				}
				retMsg.put("Type", tmpMsg.getString("Type"));
				retMsg.put("Text", tmpMsg.getString("Text"));
			} else if (tmpMsgType.equals(MsgTypeValueEnum.MSGTYPE_IMAGE.getValue()) || tmpMsgType.equals(MsgTypeValueEnum.MSGTYPE_EMOTICON.getValue())) { // 图片消息
				retMsg.put("Type", MsgTypeCodeEnum.PIC.getCode());
			} else if (tmpMsgType.equals(MsgTypeValueEnum.MSGTYPE_VOICE.getValue())) { // 语音消息
				retMsg.put("Type", MsgTypeCodeEnum.VOICE.getCode());
			} else if (tmpMsgType.equals(MsgTypeValueEnum.MSGTYPE_VERIFYMSG.getValue())) {// friends
				// 好友确认消息
				// MsgHelper.addFriend(core, userName, 3, ticket); // 确认添加好友
				retMsg.put("Type", MsgTypeCodeEnum.VERIFYMSG.getCode());

			} else if (tmpMsgType.equals(MsgTypeValueEnum.MSGTYPE_SHARECARD.getValue())) { // 共享名片
				retMsg.put("Type", MsgTypeCodeEnum.NAMECARD.getCode());
			} else if (tmpMsgType.equals(MsgTypeValueEnum.MSGTYPE_VIDEO.getValue()) || tmpMsgType.equals(MsgTypeValueEnum.MSGTYPE_MICROVIDEO.getValue())) {// viedo
				retMsg.put("Type", MsgTypeCodeEnum.VIEDO.getCode());
			} else if (tmpMsgType.equals(MsgTypeValueEnum.MSGTYPE_MEDIA.getValue())) { // 多媒体消息
				retMsg.put("Type", MsgTypeCodeEnum.MEDIA.getCode());
			} else if (tmpMsgType.equals(MsgTypeValueEnum.MSGTYPE_STATUSNOTIFY.getValue())) {// phone
				// init
				// 微信初始化消息
				logger.info("---- ----" + MsgTypeValueEnum.MSGTYPE_STATUSNOTIFY.getName());
				logger.info("");

			} else if (tmpMsgType.equals(MsgTypeValueEnum.MSGTYPE_SYS.getValue())) {// 系统消息
				retMsg.put("Type", MsgTypeCodeEnum.SYS.getCode());
			} else if (tmpMsgType.equals(MsgTypeValueEnum.MSGTYPE_RECALLED.getValue())) { // 撤回消息

			} else {
				logger.info("Useless msg");
			}
			retMsgList.add(retMsg);
			//
			logger.info("收到一条来自 " + fromUserName + " 的 " + retMsg.getString("Type") + " 消息：");
			logger.info(retMsg.toJSONString());
		}
		return retMsgList;
	}

	/**
	 * 获取微信联系人
	 */
	private void fetchContacts() {
		Map<String, Object> loginInfo = core.getLoginInfo();
		String url = String.format(URLEnum.WEB_WX_GET_CONTACT.getUrl(), loginInfo.get(StorageLoginInfoEnum.url.getKey()));
		Map<String, Object> paramMap = core.newParamMap();
		HttpEntity entity = core.getMyHttpClient().doPost(url, JSON.toJSONString(paramMap));

		try {
			String result = EntityUtils.toString(entity, Consts.UTF_8);
			JSONObject fullFriendsJsonList = JSON.parseObject(result);
			// 查看seq是否为0，0表示好友列表已全部获取完毕，若大于0，则表示好友列表未获取完毕，当前的字节数（断点续传）
			long seq = 0;
			long currentTime = 0L;
			List<BasicNameValuePair> params = new ArrayList<BasicNameValuePair>();
			if (fullFriendsJsonList.get("Seq") != null) {
				seq = fullFriendsJsonList.getLong("Seq");
				currentTime = new Date().getTime();
			}
			core.setMemberCount(fullFriendsJsonList.getInteger(StorageLoginInfoEnum.MemberCount.getKey()));
			JSONArray members = fullFriendsJsonList.getJSONArray(StorageLoginInfoEnum.MemberList.getKey());
			// 循环获取seq直到为0，即获取全部好友列表 ==0：好友获取完毕 >0：好友未获取完毕，此时seq为已获取的字节数
			while (seq > 0) {
				// 设置seq传参
				params.add(new BasicNameValuePair("r", String.valueOf(currentTime)));
				params.add(new BasicNameValuePair("seq", String.valueOf(seq)));
				entity = core.getMyHttpClient().doGet(url, params, false, null);

				params.remove(new BasicNameValuePair("r", String.valueOf(currentTime)));
				params.remove(new BasicNameValuePair("seq", String.valueOf(seq)));

				result = EntityUtils.toString(entity, Consts.UTF_8);
				fullFriendsJsonList = JSON.parseObject(result);

				if (fullFriendsJsonList.get("Seq") != null) {
					seq = fullFriendsJsonList.getLong("Seq");
					currentTime = new Date().getTime();
				}

				// 累加好友列表
				members.addAll(fullFriendsJsonList.getJSONArray(StorageLoginInfoEnum.MemberList.getKey()));
			}
			core.setMemberCount(members.size());
			for (Iterator<?> iterator = members.iterator(); iterator.hasNext();) {
				JSONObject member = (JSONObject) iterator.next();
				this.addWxMember(member);
			}
			return;
		} catch (Exception e) {
			logger.warn(e.getMessage(), e);
		}
		return;
	}

	/**
	 * 获取群好友及群好友
	 */
	private void fetchGroups() {
		Map<String, Object> loginInfo = core.getLoginInfo();
		String url = String.format(URLEnum.WEB_WX_BATCH_GET_CONTACT.getUrl(), loginInfo.get(StorageLoginInfoEnum.url.getKey()), new Date().getTime(), loginInfo.get(StorageLoginInfoEnum.pass_ticket.getKey()));
		Map<String, Object> paramMap = core.newParamMap();
		List<String> groupNames = core.getGroupIdList();
		int groupCount = groupNames.size();
		paramMap.put("Count", groupCount);
		List<Map<String, String>> list = new ArrayList<Map<String, String>>(groupCount);
		HashMap<String, String> tempMap = null;
		for (int i = 0; i < groupCount; i++) {
			tempMap = new HashMap<String, String>();
			tempMap.put("UserName", groupNames.get(i));
			tempMap.put("EncryChatRoomId", "");
			list.add(tempMap);
		}
		paramMap.put("List", list);
		HttpEntity entity = core.getMyHttpClient().doPost(url, JSON.toJSONString(paramMap));
		try {
			String text = EntityUtils.toString(entity, Consts.UTF_8);
			JSONObject obj = JSON.parseObject(text);
			JSONArray contactList = obj.getJSONArray("ContactList");
			String userName = null, nickName = null;
			for (int i = 0; i < contactList.size(); i++) { // 群好友
				JSONObject tmpObj = contactList.getJSONObject(i);
				userName = tmpObj.getString("UserName");
				if (userName.indexOf("@@") > -1) { // 群
					nickName = tmpObj.getString("NickName");
					core.setMember(userName, tmpObj);
					core.setNickName(userName, nickName);
					if (core.addGroupId(userName)) {
						logger.info("添加 群组 " + userName);
					} else {
						logger.info("刷新 群组 " + userName);
					}
				}
			}
		} catch (Exception e) {
			logger.warn(e.getMessage());
		}
	}

	/**
	 * 检查登陆状态
	 *
	 * @param result
	 * @return
	 */
	private String checklogin(String result) {
		String regEx = "window.code=(\\d+)";
		Matcher matcher = CommonTools.getMatcher(regEx, result);
		if (matcher.find()) {
			return matcher.group(1);
		}
		return null;
	}

	private String createDeviceId() {
		return "e" + String.valueOf(new Random().nextLong()).substring(1, 16);
	}

	/**
	 * 解析登录返回的消息，如果成功登录，则message为空
	 * 
	 * @param result
	 * @return
	 */
	private String extractLoginMessage(String result) {
		String[] strArr = result.split("<message>");
		String[] rs = strArr[1].split("</message>");
		if (rs != null && rs.length > 1) {
			return rs[0];
		}
		return "";
	}

	/**
	 * 处理登陆信息
	 *
	 * @author https://github.com/yaphone
	 * @date 2017年4月9日 下午12:16:26
	 * @param result
	 * @return 错误消息
	 */
	private String processLoginInfo(String loginContent) {
		String regEx = "window.redirect_uri=\"(\\S+)\";";
		Matcher matcher = CommonTools.getMatcher(regEx, loginContent);
		if (matcher.find()) {
			Map<String, Object> loginInfo = core.getLoginInfo();
			String originalUrl = matcher.group(1);
			String url = originalUrl.substring(0, originalUrl.lastIndexOf('/')); // https://wx2.qq.com/cgi-bin/mmwebwx-bin
			loginInfo.put("url", url);
			Iterator<Entry<String, List<String>>> iterator = wxDomainUrlMap.entrySet().iterator();
			Map.Entry<String, List<String>> entry;
			String fileUrl;
			String syncUrl;
			while (iterator.hasNext()) {
				entry = iterator.next();
				String indexUrl = entry.getKey();
				fileUrl = "https://" + entry.getValue().get(0) + "/cgi-bin/mmwebwx-bin";
				syncUrl = "https://" + entry.getValue().get(1) + "/cgi-bin/mmwebwx-bin";
				if (loginInfo.get("url").toString().contains(indexUrl)) {
					core.setIndexUrl(indexUrl);
					loginInfo.put("fileUrl", fileUrl);
					loginInfo.put("syncUrl", syncUrl);
					break;
				}
			}
			if (loginInfo.get("fileUrl") == null && loginInfo.get("syncUrl") == null) {
				loginInfo.put("fileUrl", url);
				loginInfo.put("syncUrl", url);
			}
			// 尽量重用deviceid
			String deviceid = (String) loginInfo.get(StorageLoginInfoEnum.deviceid.getKey());
			if (deviceid == null) {
				deviceid = this.createDeviceId();
			}
			loginInfo.put("deviceid", deviceid); // 生成15位随机数
			loginInfo.put("BaseRequest", new ArrayList<String>());
			String text = "";

			try {
				HttpEntity entity = core.getMyHttpClient().doGet(originalUrl, null, false, null);
				text = EntityUtils.toString(entity);
			} catch (Exception e) {
				logger.warn(e.getMessage());
				return "登陆失败：" + e.getMessage();
			}
			// add by 默非默 2017-08-01 22:28:09
			// 如果登录被禁止时，则登录返回的message内容不为空，下面代码则判断登录内容是否为空，不为空则退出程序
			String msg = extractLoginMessage(text);
			if (!"".equals(msg)) {
				logger.warn(msg);
				return msg;
			}
			Document doc = CommonTools.xmlParser(text);
			if (doc != null) {
				loginInfo.put(StorageLoginInfoEnum.skey.getKey(), doc.getElementsByTagName(StorageLoginInfoEnum.skey.getKey()).item(0).getFirstChild().getNodeValue());
				loginInfo.put(StorageLoginInfoEnum.wxsid.getKey(), doc.getElementsByTagName(StorageLoginInfoEnum.wxsid.getKey()).item(0).getFirstChild().getNodeValue());
				loginInfo.put(StorageLoginInfoEnum.wxuin.getKey(), doc.getElementsByTagName(StorageLoginInfoEnum.wxuin.getKey()).item(0).getFirstChild().getNodeValue());
				loginInfo.put(StorageLoginInfoEnum.pass_ticket.getKey(), doc.getElementsByTagName(StorageLoginInfoEnum.pass_ticket.getKey()).item(0).getFirstChild().getNodeValue());
			}
			return null;
		}
		return "登陆失败";
	}

	/**
	 * 检查（是否有新消息）
	 * 
	 * @author https://github.com/yaphone
	 * @date 2017年4月16日 上午11:11:34
	 * @return
	 * 
	 */
	private Map<String, String> doSyncCheck() {
		Map<String, String> resultMap = new HashMap<String, String>();
		// 组装请求URL和参数
		Map<String, Object> loginInfo = core.getLoginInfo();
		String url = loginInfo.get(StorageLoginInfoEnum.syncUrl.getKey()) + URLEnum.SYNC_CHECK_URL.getUrl();
		List<BasicNameValuePair> params = new ArrayList<BasicNameValuePair>();
		for (BaseParamEnum baseRequest : BaseParamEnum.values()) {
			params.add(new BasicNameValuePair(baseRequest.param().toLowerCase(), loginInfo.get(baseRequest.value()).toString()));
		}
		params.add(new BasicNameValuePair("r", String.valueOf(new Date().getTime())));
		params.add(new BasicNameValuePair("synckey", (String) loginInfo.get("synckey")));
		params.add(new BasicNameValuePair("_", String.valueOf(new Date().getTime())));

		try {
			HttpEntity entity = core.getMyHttpClient().doGet(url, params, true, null);
			if (entity == null) {
				resultMap.put("retcode", "9999");
				resultMap.put("selector", "9999");
			} else {
				String text = EntityUtils.toString(entity);
				String regEx = "window.synccheck=\\{retcode:\"(\\d+)\",selector:\"(\\d+)\"\\}";
				Matcher matcher = CommonTools.getMatcher(regEx, text);
				if (!matcher.find() || matcher.group(1).equals("2")) {
					logger.warn(String.format("意外的同步检查结果: %s", text));
				} else {
					resultMap.put("retcode", matcher.group(1));
					resultMap.put("selector", matcher.group(2));
				}
			}
		} catch (Exception e) {
			logger.warn(e.getMessage());
		}
		return resultMap;
	}

	/**
	 * 同步拉取消息
	 * 
	 * @author https://github.com/yaphone
	 * @date 2017年5月12日 上午12:24:55
	 * @return
	 */
	private JSONObject pullSyncMsgs() {
		JSONObject result = null;
		Map<String, Object> loginInfo = core.getLoginInfo();
		String url = String.format(URLEnum.WEB_WX_SYNC_URL.getUrl(), loginInfo.get(StorageLoginInfoEnum.url.getKey()), loginInfo.get(StorageLoginInfoEnum.wxsid.getKey()), loginInfo.get(StorageLoginInfoEnum.skey.getKey()),
				loginInfo.get(StorageLoginInfoEnum.pass_ticket.getKey()));
		Map<String, Object> paramMap = core.newParamMap();
		paramMap.put(StorageLoginInfoEnum.SyncKey.getKey(), loginInfo.get(StorageLoginInfoEnum.SyncKey.getKey()));
		paramMap.put("rr", -new Date().getTime() / 1000);
		try {
			String paramStr = JSON.toJSONString(paramMap);
			HttpEntity entity = core.getMyHttpClient().doPost(url, paramStr);
			String text = EntityUtils.toString(entity, Consts.UTF_8);
			JSONObject obj = JSON.parseObject(text);
			if (obj.getJSONObject("BaseResponse").getInteger("Ret") != 0) {
				result = null;
			} else {
				result = obj;
				//
				loginInfo.put(StorageLoginInfoEnum.SyncKey.getKey(), obj.getJSONObject("SyncCheckKey"));
				JSONArray syncArray = obj.getJSONObject(StorageLoginInfoEnum.SyncKey.getKey()).getJSONArray("List");
				StringBuilder sb = new StringBuilder();
				for (int i = 0; i < syncArray.size(); i++) {
					sb.append(syncArray.getJSONObject(i).getString("Key") + "_" + syncArray.getJSONObject(i).getString("Val") + "|");
				}
				String synckey = sb.toString();
				loginInfo.put(StorageLoginInfoEnum.synckey.getKey(), synckey.substring(0, synckey.length() - 1));// 1_656161336|2_656161626|3_656161313|11_656159955|13_656120033|201_1492273724|1000_1492265953|1001_1492250432|1004_1491805192
			}
		} catch (Exception e) {
			logger.warn(e.getMessage());
		}
		return result;
	}

	/**
	 * 接收消息
	 */
	private Thread heatbeatThead = null;
	private int heatbeatInterval = 1000;

	private void stopHeatbeat() {
		if (heatbeatThead != null && heatbeatThead.isAlive()) {
			try {
				heatbeatThead.interrupt();
			} catch (Exception ex) {
				logger.warn(ex.getMessage());
			}
			logger.info("心跳已停止");
		}
	}

	private void startHeatbeat() {
		this.stopHeatbeat();
		//
		heatbeatThead = new Thread() {
			public void run() {
				while (isRunning) {
					try {
						SleepUtils.sleep(heatbeatInterval);
						//
						logger.info("------ heatbeat ------");
						Map<String, String> resultMap = doSyncCheck();
						String retcode = resultMap.get("retcode");
						String selector = resultMap.get("selector");
						String message = null;
						if (retcode.equals(RetCodeEnum.UNKOWN.getCode())) {
							logger.warn(RetCodeEnum.UNKOWN.getMessage());
						} else if (retcode.equals(RetCodeEnum.NOT_LOGIN_WARN.getCode())) { // 未登录或已退出
							message = RetCodeEnum.NOT_LOGIN_WARN.getMessage();
							logger.info(message);
							core.setAlive(false);
							core.setLastMessage(message);
						} else if (retcode.equals(RetCodeEnum.LOGIN_OTHERWHERE.getCode())) { // 其它地方登陆
							message = RetCodeEnum.LOGIN_OTHERWHERE.getMessage();
							logger.info(message);
							core.setAlive(false);
							core.setLastMessage(message);
						} else if (retcode.equals(RetCodeEnum.INVALID_COOKIE.getCode())) { // 移动端退出
							logger.warn(RetCodeEnum.INVALID_COOKIE.getMessage());
							logger.warn(message);
						} else if (retcode.equals(RetCodeEnum.SUCCESS.getCode())) {
							JSONObject msgObj = pullSyncMsgs();
							if (selector.equals("2")) {// 新的消息
								try {
									JSONArray msgList = new JSONArray();
									msgList = msgObj.getJSONArray("AddMsgList");
									msgList = filterWxMsg(msgList);
									for (int j = 0; j < msgList.size(); j++) {
										BaseMsg baseMsg = JSON.toJavaObject(msgList.getJSONObject(j), BaseMsg.class);
										core.getMsgList().add(baseMsg);
									}
								} catch (Exception e) {
									logger.warn(e.getMessage());
								}
							} else if (selector.equals("7")) {
								pullSyncMsgs();
							} else if (selector.equals("6")) {
								if (msgObj != null) {
									try {
										JSONArray msgList = new JSONArray();
										msgList = msgObj.getJSONArray("AddMsgList");
										JSONArray modContactList = msgObj.getJSONArray("ModContactList"); // 存在删除或者新增的好友信息
										msgList = filterWxMsg(msgList);
										for (int j = 0; j < msgList.size(); j++) {
											JSONObject userInfo = modContactList.getJSONObject(j);
											addWxMember(userInfo);
										}
									} catch (Exception e) {
										logger.warn(e.getMessage());
									}
								}
							}
						} else {
							JSONObject obj = pullSyncMsgs();
							logger.info("------ Others ------");
							// logger.info(JSONObject.toJSONString(obj));
						}
					} catch (Exception e) {
						logger.warn(e.getMessage());
					}
				}
			}
		};
		//
		heatbeatThead.start();
		logger.info("心跳已开启");
	}

	//
	private Thread dataMonitorThead = null;
	private int dataMonitorInterval = 30000;

	private void stopDataMonitor() {
		if (dataMonitorThead != null && dataMonitorThead.isAlive()) {
			try {
				dataMonitorThead.interrupt();
			} catch (Exception ex) {
				logger.warn(ex.getMessage());
			}
			logger.info("数据监测已停止");
		}
	}

	private void startDataMonitor() {
		this.stopDataMonitor();
		//
		dataMonitorThead = new Thread() {
			public void run() {
				while (isRunning) {
					try {
						SleepUtils.sleep(dataMonitorInterval);
						boolean groupSyncFlag = false;
						if (core.hasNoneSyncGroups()) {
							fetchContacts();
							fetchGroups();
							core.setLastSyncGroupTs();
							groupSyncFlag = true;
						}
						if (groupSyncFlag || core.hasDataChanges()) {
							core.saveStoreData();
							core.setDataSavedTs();
						}
					} catch (Exception e) {
						logger.warn(e.getMessage());
					}
				}
			}
		};
		//
		dataMonitorThead.start();
		logger.info("数据监测已开启");
	}

	public boolean doLogout() {
		boolean result = false;
		//
		this.stopHeatbeat();
		//
		if (core.isAlive()) {
			Map<String, Object> loginInfo = core.getLoginInfo();
			String url = String.format(URLEnum.WEB_WX_LOGOUT.getUrl(), loginInfo.get(StorageLoginInfoEnum.url.getKey()));
			List<BasicNameValuePair> params = new ArrayList<BasicNameValuePair>();
			params.add(new BasicNameValuePair("redirect", "1"));
			params.add(new BasicNameValuePair("type", "1"));
			params.add(new BasicNameValuePair("skey", (String) loginInfo.get(StorageLoginInfoEnum.skey.getKey())));
			try {
				HttpEntity entity = core.getMyHttpClient().doGet(url, params, false, null);
				String text = EntityUtils.toString(entity, Consts.UTF_8); // 无消息
				logger.info(text);
				core.setAlive(false);
				//
				result = true;
			} catch (Exception e) {
				logger.warn(e.getMessage());
			}
		}
		//
		core.reset();
		//
		return result;
	}

}
