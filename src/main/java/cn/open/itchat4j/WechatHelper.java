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
import cn.open.itchat4j.core.MsgCenter;
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
 */
public class WechatHelper {
	private static Logger logger = LoggerFactory.getLogger(WechatHelper.class);

	private Core core = Core.getInstance();

	private WechatHelper() {
		System.setProperty("jsse.enableSNIExtension", "false"); // 防止SSL错误
	}

	private static WechatHelper instance = new WechatHelper();

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
			core.saveStoreData();
			//
			if (receivingThread != null && receivingThread.isAlive()) {
				receivingThread.interrupt();
			}
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
			// TODO 做些启动的处理

			//
			logger.info("Helper已启动 OK");
		} finally {
			isRunningLock.unlock();
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
	public String getUuid(boolean refresh) {
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
					if ((ResultEnum.SUCCESS.getCode().equals(matcher.group(1)))) {
						retUuid = matcher.group(2);
						core.setUuid(retUuid);
						logger.info("已刷新了 登陆uuid");
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
	public String getQrImageUrl(String uuid) {
		if (uuid == null || uuid.trim().equals("")) {
			uuid = this.getUuid(true);// 获取新的uuid
		}
		return uuid == null ? null : URLEnum.QRCODE_URL.getUrl() + uuid;
	}

	/**
	 * 获取登陆uuid对应的二维码字节流数据
	 */
	public ImageBytes getQrImageBytes(String uuid) {
		try {
			String qrUrl = getQrImageUrl(uuid);
			if (qrUrl == null) {
				return null;
			}
			HttpEntity entity = core.getMyHttpClient().doGet(qrUrl, null, true, null);
			ImageBytes qrImageBytes = new ImageBytes();
			qrImageBytes.data = EntityUtils.toByteArray(entity);
			return qrImageBytes;
		} catch (Exception e) {
			logger.warn(e.getMessage());
			return null;
		}
	}

	/**
	 * 获取并打开登陆二维码（用于测试/调试扫码登陆）
	 */
	@Deprecated
	public boolean getAndOpenQrImage(String uuid, String qrPathDir) {
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

	public String getPushLoginUuid() {
		try {
			String url = (String) core.getLoginInfo().get("url");
			String wxuin = (String) core.getLoginInfo().get("wxuin");
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

	/**
	 * 登陆
	 */
	public Boolean tryToLogin() {
		tryToLoginLock.lock();
		try {
			if (tryToLoginFlag) {
				return null;
			}
			tryToLoginFlag = true;
			//
			boolean isLoggedIn = false;
			boolean waitingConfirm = false;
			int checkInterval = 1000;
			while (!isLoggedIn && isRunning) {
				try {
					if (!waitingConfirm) {
						String uuid = this.getPushLoginUuid();
						if (uuid == null) {
							logger.warn("登陆已过期，需要重新登陆");
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
						} else {
							logger.warn(errMsg);
						}
						break;
					} else if (ResultEnum.WAIT_SCAN.getCode().equals(status)) {
						logger.info(ResultEnum.WAIT_SCAN.getMessage());
						waitingConfirm = true;
					} else if (ResultEnum.WAIT_CONFIRM.getCode().equals(status)) {
						logger.info(ResultEnum.WAIT_CONFIRM.getMessage());
						waitingConfirm = true;
					} else if (ResultEnum.WAIT_TIMEOUT.getCode().equals(status)) {
						logger.info(ResultEnum.WAIT_TIMEOUT.getMessage());
						waitingConfirm = true;
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
	public boolean initBasicInfo() {
		if (!core.isAlive()) {
			return false;
		}
		//
		core.setLastNormalRetCodeTime(System.currentTimeMillis());
		// 组装请求URL和参数
		String url = String.format(URLEnum.INIT_URL.getUrl(), core.getLoginInfo().get(StorageLoginInfoEnum.url.getKey()), String.valueOf(System.currentTimeMillis() / 3158L),
				core.getLoginInfo().get(StorageLoginInfoEnum.pass_ticket.getKey()));

		Map<String, Object> paramMap = core.newParamMap();

		// 请求初始化接口
		HttpEntity entity = core.getMyHttpClient().doPost(url, JSON.toJSONString(paramMap));
		try {
			String result = EntityUtils.toString(entity, Consts.UTF_8);
			logger.info("------ webWxInit ------");
			logger.info(result);
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
				core.getLoginInfo().put(StorageLoginInfoEnum.InviteStartCount.getKey(), obj.getInteger(StorageLoginInfoEnum.InviteStartCount.getKey()));
				core.getLoginInfo().put(StorageLoginInfoEnum.SyncKey.getKey(), syncKey);

				JSONArray syncArray = syncKey.getJSONArray("List");
				StringBuilder sb = new StringBuilder();
				for (int i = 0; i < syncArray.size(); i++) {
					sb.append(syncArray.getJSONObject(i).getString("Key") + "_" + syncArray.getJSONObject(i).getString("Val") + "|");
				}
				// 1_661706053|2_661706420|3_661706415|1000_1494151022|
				String synckey = sb.toString();

				// 1_661706053|2_661706420|3_661706415|1000_1494151022
				core.getLoginInfo().put(StorageLoginInfoEnum.synckey.getKey(), synckey.substring(0, synckey.length() - 1));// 1_656161336|2_656161626|3_656161313|11_656159955|13_656120033|201_1492273724|1000_1492265953|1001_1492250432|1004_1491805192
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
	public boolean initStatusNotify() {
		// 组装请求URL和参数
		String url = String.format(URLEnum.STATUS_NOTIFY_URL.getUrl(), core.getLoginInfo().get(StorageLoginInfoEnum.pass_ticket.getKey()));

		Map<String, Object> paramMap = core.newParamMap();
		paramMap.put(StatusNotifyParamEnum.CODE.param(), StatusNotifyParamEnum.CODE.value());
		paramMap.put(StatusNotifyParamEnum.FROM_USERNAME.param(), core.getUserName());
		paramMap.put(StatusNotifyParamEnum.TO_USERNAME.param(), core.getUserName());
		paramMap.put(StatusNotifyParamEnum.CLIENT_MSG_ID.param(), System.currentTimeMillis());
		String paramStr = JSON.toJSONString(paramMap);

		try {
			HttpEntity entity = core.getMyHttpClient().doPost(url, paramStr);
			String result = EntityUtils.toString(entity, Consts.UTF_8);
			logger.info("------ wxStatusNotify ------");
			logger.info(result);
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
			if (!core.getPublicUserIdList().contains(userName)) {
				core.getPublicUserIdList().add(userName);
			}
		} else if (Config.API_SPECIAL_USER.contains(userName)) { // 特殊账号
			if (!core.getSpecialUserIdList().contains(userName)) {
				core.getSpecialUserIdList().add(userName);
			}
		} else if (userName.indexOf("@@") != -1) { // 群聊
			if (!core.getGroupIdList().contains(userName)) {
				core.getGroupIdList().add(userName);
				System.out.println("群聊:" + userName);
			}
		} else if (userName.equals(core.getUserName())) { // 自己
			core.getContactIdList().remove(userName);
		} else { // 普通联系人
			if (!core.getContactIdList().contains(userName)) {
				core.getContactIdList().add(userName);
			}
		}
	}

	/**
	 * 接收消息
	 */
	private Thread receivingThread = null;

	public void startReceiving() {
		if (receivingThread != null && receivingThread.isAlive()) {
			return;
		}
		//
		receivingThread = new Thread(new Runnable() {
			int retryCount = 0;

			//
			@Override
			public void run() {
				while (isRunning) {
					try {
						SleepUtils.sleep(10);
						//
						Map<String, String> resultMap = syncCheck();
						logger.info("------ received ------");
						logger.info(JSONObject.toJSONString(resultMap));
						String retcode = resultMap.get("retcode");
						String selector = resultMap.get("selector");
						if (retcode.equals(RetCodeEnum.UNKOWN.getCode())) {
							logger.info(RetCodeEnum.UNKOWN.getMessage());
							continue;
						} else if (retcode.equals(RetCodeEnum.NOT_LOGIN_WARN.getCode())) { // 退出
							logger.info(RetCodeEnum.NOT_LOGIN_WARN.getMessage());
							core.setAlive(false);
							break;
						} else if (retcode.equals(RetCodeEnum.LOGIN_OTHERWHERE.getCode())) { // 其它地方登陆
							logger.info(RetCodeEnum.LOGIN_OTHERWHERE.getMessage());
							core.setAlive(false);
							break;
						} else if (retcode.equals(RetCodeEnum.MOBILE_LOGIN_OUT.getCode())) { // 移动端退出
							logger.info(RetCodeEnum.MOBILE_LOGIN_OUT.getMessage());
							core.setAlive(false);
							break;
						} else if (retcode.equals(RetCodeEnum.SUCCESS.getCode())) {
							core.setLastNormalRetCodeTime(System.currentTimeMillis()); // 最后收到正常报文时间
							JSONObject msgObj = webWxSync();
							if (selector.equals("2")) {
								if (msgObj != null) {
									try {
										JSONArray msgList = new JSONArray();
										msgList = msgObj.getJSONArray("AddMsgList");
										msgList = MsgCenter.produceMsg(msgList);
										for (int j = 0; j < msgList.size(); j++) {
											BaseMsg baseMsg = JSON.toJavaObject(msgList.getJSONObject(j), BaseMsg.class);
											core.getMsgList().add(baseMsg);
										}
									} catch (Exception e) {
										logger.warn(e.getMessage());
									}
								}
							} else if (selector.equals("7")) {
								webWxSync();
							} else if (selector.equals("4")) {
								continue;
							} else if (selector.equals("3")) {
								continue;
							} else if (selector.equals("6")) {
								if (msgObj != null) {
									try {
										JSONArray msgList = new JSONArray();
										msgList = msgObj.getJSONArray("AddMsgList");
										JSONArray modContactList = msgObj.getJSONArray("ModContactList"); // 存在删除或者新增的好友信息
										msgList = MsgCenter.produceMsg(msgList);
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
							JSONObject obj = webWxSync();
							logger.info(JSONObject.toJSONString(obj));
						}
					} catch (Exception e) {
						logger.warn(e.getMessage());
						//
						retryCount += 1;
						if (retryCount > core.getReceivingRetryCount()) {
							retryCount = 0;
							core.setAlive(false);
						} else {
							try {
								Thread.sleep(1000);
							} catch (InterruptedException e1) {
								logger.warn(e.getMessage());
							}
						}
					}
				}
			}
		});
		//
		receivingThread.start();
	}

	/**
	 * 获取微信联系人
	 */
	public void fetchContacts() {
		String url = String.format(URLEnum.WEB_WX_GET_CONTACT.getUrl(), core.getLoginInfo().get(StorageLoginInfoEnum.url.getKey()));
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
	public void fetchGroups() {
		String url = String.format(URLEnum.WEB_WX_BATCH_GET_CONTACT.getUrl(), core.getLoginInfo().get(StorageLoginInfoEnum.url.getKey()), new Date().getTime(), core.getLoginInfo().get(StorageLoginInfoEnum.pass_ticket.getKey()));
		Map<String, Object> paramMap = core.newParamMap();
		paramMap.put("Count", core.getGroupIdList().size());
		List<Map<String, String>> list = new ArrayList<Map<String, String>>();
		for (int i = 0; i < core.getGroupIdList().size(); i++) {
			HashMap<String, String> map = new HashMap<String, String>();
			map.put("UserName", core.getGroupIdList().get(i));
			map.put("EncryChatRoomId", "");
			list.add(map);
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
					if (!core.getGroupIdList().contains(userName)) {
						core.getGroupIdList().add(userName);
					}
					System.out.println("群聊X:" + userName);
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
			String originalUrl = matcher.group(1);
			String url = originalUrl.substring(0, originalUrl.lastIndexOf('/')); // https://wx2.qq.com/cgi-bin/mmwebwx-bin
			core.getLoginInfo().put("url", url);
			Map<String, List<String>> possibleUrlMap = this.getPossibleUrlMap();
			Iterator<Entry<String, List<String>>> iterator = possibleUrlMap.entrySet().iterator();
			Map.Entry<String, List<String>> entry;
			String fileUrl;
			String syncUrl;
			while (iterator.hasNext()) {
				entry = iterator.next();
				String indexUrl = entry.getKey();
				fileUrl = "https://" + entry.getValue().get(0) + "/cgi-bin/mmwebwx-bin";
				syncUrl = "https://" + entry.getValue().get(1) + "/cgi-bin/mmwebwx-bin";
				if (core.getLoginInfo().get("url").toString().contains(indexUrl)) {
					core.setIndexUrl(indexUrl);
					core.getLoginInfo().put("fileUrl", fileUrl);
					core.getLoginInfo().put("syncUrl", syncUrl);
					break;
				}
			}
			if (core.getLoginInfo().get("fileUrl") == null && core.getLoginInfo().get("syncUrl") == null) {
				core.getLoginInfo().put("fileUrl", url);
				core.getLoginInfo().put("syncUrl", url);
			}
			// 尽量重用deviceid
			String deviceid = (String) core.getLoginInfo().get(StorageLoginInfoEnum.deviceid.getKey());
			if (deviceid == null) {
				deviceid = "e" + String.valueOf(new Random().nextLong()).substring(1, 16);
			}
			core.getLoginInfo().put("deviceid", deviceid); // 生成15位随机数
			core.getLoginInfo().put("BaseRequest", new ArrayList<String>());
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
				core.getLoginInfo().put(StorageLoginInfoEnum.skey.getKey(), doc.getElementsByTagName(StorageLoginInfoEnum.skey.getKey()).item(0).getFirstChild().getNodeValue());
				core.getLoginInfo().put(StorageLoginInfoEnum.wxsid.getKey(), doc.getElementsByTagName(StorageLoginInfoEnum.wxsid.getKey()).item(0).getFirstChild().getNodeValue());
				core.getLoginInfo().put(StorageLoginInfoEnum.wxuin.getKey(), doc.getElementsByTagName(StorageLoginInfoEnum.wxuin.getKey()).item(0).getFirstChild().getNodeValue());
				core.getLoginInfo().put(StorageLoginInfoEnum.pass_ticket.getKey(), doc.getElementsByTagName(StorageLoginInfoEnum.pass_ticket.getKey()).item(0).getFirstChild().getNodeValue());
			}
			return null;
		}
		return "登陆失败";
	}

	private Map<String, List<String>> getPossibleUrlMap() {
		Map<String, List<String>> possibleUrlMap = new HashMap<String, List<String>>();
		possibleUrlMap.put("wx.qq.com", new ArrayList<String>() {
			/**
			 * 
			 */
			private static final long serialVersionUID = 1L;

			{
				add("file.wx.qq.com");
				add("webpush.wx.qq.com");
			}
		});

		possibleUrlMap.put("wx2.qq.com", new ArrayList<String>() {
			/**
			 * 
			 */
			private static final long serialVersionUID = 1L;

			{
				add("file.wx2.qq.com");
				add("webpush.wx2.qq.com");
			}
		});
		possibleUrlMap.put("wx8.qq.com", new ArrayList<String>() {
			/**
			 * 
			 */
			private static final long serialVersionUID = 1L;

			{
				add("file.wx8.qq.com");
				add("webpush.wx8.qq.com");
			}
		});

		possibleUrlMap.put("web2.wechat.com", new ArrayList<String>() {
			/**
			 * 
			 */
			private static final long serialVersionUID = 1L;

			{
				add("file.web2.wechat.com");
				add("webpush.web2.wechat.com");
			}
		});
		possibleUrlMap.put("wechat.com", new ArrayList<String>() {
			/**
			 * 
			 */
			private static final long serialVersionUID = 1L;

			{
				add("file.web.wechat.com");
				add("webpush.web.wechat.com");
			}
		});
		return possibleUrlMap;
	}

	/**
	 * 同步消息 sync the messages
	 * 
	 * @author https://github.com/yaphone
	 * @date 2017年5月12日 上午12:24:55
	 * @return
	 */
	private JSONObject webWxSync() {
		JSONObject result = null;
		String url = String.format(URLEnum.WEB_WX_SYNC_URL.getUrl(), core.getLoginInfo().get(StorageLoginInfoEnum.url.getKey()), core.getLoginInfo().get(StorageLoginInfoEnum.wxsid.getKey()),
				core.getLoginInfo().get(StorageLoginInfoEnum.skey.getKey()), core.getLoginInfo().get(StorageLoginInfoEnum.pass_ticket.getKey()));
		Map<String, Object> paramMap = core.newParamMap();
		paramMap.put(StorageLoginInfoEnum.SyncKey.getKey(), core.getLoginInfo().get(StorageLoginInfoEnum.SyncKey.getKey()));
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
				core.getLoginInfo().put(StorageLoginInfoEnum.SyncKey.getKey(), obj.getJSONObject("SyncCheckKey"));
				JSONArray syncArray = obj.getJSONObject(StorageLoginInfoEnum.SyncKey.getKey()).getJSONArray("List");
				StringBuilder sb = new StringBuilder();
				for (int i = 0; i < syncArray.size(); i++) {
					sb.append(syncArray.getJSONObject(i).getString("Key") + "_" + syncArray.getJSONObject(i).getString("Val") + "|");
				}
				String synckey = sb.toString();
				core.getLoginInfo().put(StorageLoginInfoEnum.synckey.getKey(), synckey.substring(0, synckey.length() - 1));// 1_656161336|2_656161626|3_656161313|11_656159955|13_656120033|201_1492273724|1000_1492265953|1001_1492250432|1004_1491805192
			}
		} catch (Exception e) {
			logger.warn(e.getMessage());
		}
		return result;

	}

	/**
	 * 检查是否有新消息 check whether there's a message
	 * 
	 * @author https://github.com/yaphone
	 * @date 2017年4月16日 上午11:11:34
	 * @return
	 * 
	 */
	private Map<String, String> syncCheck() {
		Map<String, String> resultMap = new HashMap<String, String>();
		// 组装请求URL和参数
		String url = core.getLoginInfo().get(StorageLoginInfoEnum.syncUrl.getKey()) + URLEnum.SYNC_CHECK_URL.getUrl();
		List<BasicNameValuePair> params = new ArrayList<BasicNameValuePair>();
		for (BaseParamEnum baseRequest : BaseParamEnum.values()) {
			params.add(new BasicNameValuePair(baseRequest.param().toLowerCase(), core.getLoginInfo().get(baseRequest.value()).toString()));
		}
		params.add(new BasicNameValuePair("r", String.valueOf(new Date().getTime())));
		params.add(new BasicNameValuePair("synckey", (String) core.getLoginInfo().get("synckey")));
		params.add(new BasicNameValuePair("_", String.valueOf(new Date().getTime())));
		try {
			HttpEntity entity = core.getMyHttpClient().doGet(url, params, true, null);
			if (entity == null) {
				resultMap.put("retcode", "9999");
				resultMap.put("selector", "9999");
				return resultMap;
			}
			String text = EntityUtils.toString(entity);
			String regEx = "window.synccheck=\\{retcode:\"(\\d+)\",selector:\"(\\d+)\"\\}";
			Matcher matcher = CommonTools.getMatcher(regEx, text);
			if (!matcher.find() || matcher.group(1).equals("2")) {
				logger.warn(String.format("Unexpected sync check result: %s", text));
			} else {
				resultMap.put("retcode", matcher.group(1));
				resultMap.put("selector", matcher.group(2));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return resultMap;
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

}
