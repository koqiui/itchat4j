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
import cn.open.itchat4j.core.Core;
import cn.open.itchat4j.core.CoreDataStore;
import cn.open.itchat4j.core.CoreStateListener;
import cn.open.itchat4j.core.MsgUser;
import cn.open.itchat4j.enums.MsgTypeCodeEnum;
import cn.open.itchat4j.enums.MsgTypeValueEnum;
import cn.open.itchat4j.enums.MsgUserType;
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

	private CoreStateListener stateListener;

	private void triggerWaitingForScanListener(boolean waiting) {
		if (this.stateListener != null) {
			this.stateListener.onWaitForScan(this.getNodeName(), waiting);
		}
	}

	/** 设置是否处理接收的消息（否则不会放入接收的消息队列里） */
	public void setHandleRecvMsgs(boolean handleRecvMsgs) {
		core.setHandleRecvMsgs(handleRecvMsgs);
	}

	/** 获取节点名称 */
	public String getNodeName() {
		return core.getNodeName();
	}

	// 1
	/** 设置节点名称 */
	public void setNodeName(String nodeName) {
		core.setNodeName(nodeName);
	}

	// 2.x
	/** 初始化 */
	public void initCore() {
		this.initCore(null, null);
	}

	// 2.y
	/** 初始化 */
	public void initCore(CoreStateListener stateListener) {
		this.initCore(null, null);
	}

	// 2.z
	/** 初始化 */
	public void initCore(CoreDataStore dataStore, CoreStateListener stateListener) {
		core.doInit(dataStore);
		// core 负责触发多数事件
		core.setStateListener(stateListener);
		// 本类 负责触发部分事件
		this.stateListener = stateListener;
	}

	// 可选 是否使用新版微信id
	// cn.open.itchat4j.enums.params.UUIDParamEnum.APP_ID_OLD
	// cn.open.itchat4j.enums.params.UUIDParamEnum.APP_ID_NEW
	private boolean useNewVersion = false;

	/** 设置是否使用新版微信， 注意：针对在线的节点有效（所以，所有节点都要使用同样的版本，但新版容易登陆失败） */
	public void setUseNewVersion(boolean useNewVersion) {
		if (useNewVersion != this.useNewVersion) {
			this.useNewVersion = useNewVersion;
			//
			if (core.isAlive() && !core.isUseNewVersion().equals(useNewVersion)) {
				this.doLogout();// 清除了所有登陆相关信息
				// 很重要！！！
				core.setUseNewVersion(useNewVersion);
				//
				logger.info("切换为 " + (useNewVersion ? "新版本" : "旧版本") + " 微信重新登陆");
				this.doLogin(this.lastQrImageDir);
			}
		}
	}

	private ReentrantLock isRunningLock = new ReentrantLock();
	private volatile boolean isRunning = false;

	// 3
	/** 启动节点，启动后才能处理在线交互 */
	public void startup() {
		try {
			isRunningLock.lock();
			//
			if (isRunning) {
				logger.warn("WechatHelper正在运行中");
				return;
			}
			isRunning = true;
			//
			logger.info("WechatHelper 已启动");
		} finally {
			isRunningLock.unlock();
		}
	}

	// last
	/** 应用停止时执行（下线并停止各线程） */
	public void shutdown() {
		try {
			isRunningLock.lock();
			//
			if (!isRunning) {
				logger.warn("WechatHelper不在运行中");
				return;
			}
			isRunning = false;
			//
			this.stopHeatbeat();
			this.stopDataMonitor();
			//
			if (core.isAlive()) {
				boolean groupSyncFlag = false;
				if (core.hasNoneSyncGroups()) {
					fetchGroups();
					core.setLastSyncGroupTs();
					groupSyncFlag = true;
				}
				if (groupSyncFlag || core.hasDataChanges()) {
					core.saveStoreData();
				}
				//
				core.setAlive(false);
				readyFlag = false;
			}
			//
			tryingToLogin = false;
			if (waitingForLoginScan) {
				waitingForLoginScan = false;
				triggerWaitingForScanListener(waitingForLoginScan);
			}
			//
			logger.info("WechatHelper 已停止");
		} finally {
			isRunningLock.unlock();
		}
	}

	/**
	 * 是否在线
	 */
	public boolean isAlive() {
		return core.isAlive();
	}

	/**
	 * 是否正在等着扫码登陆（如果是不要再调用登陆）
	 */

	public boolean isWaitingForLoginScan() {
		return this.waitingForLoginScan;
	}

	// 4.x
	/** 登陆集成方法(web应用) */
	public void doLogin() {
		this.doLogin(null);
	}

	private String lastQrImageDir;

	// 4.y
	/**
	 * 登陆集成方法
	 * 
	 * @param qrImageDir
	 *            如果指定图片保存目录则从本地打开（仅用于测试，web应用下不要设置，从getQrImageUrl(false)即可获取 二维码图片url ）
	 */
	public void doLogin(String qrImageDir) {
		// 缓存用于自动重新登陆
		this.lastQrImageDir = qrImageDir;
		//
		if (!isRunning) {
			logger.warn("Helper未启动或已停止，不能登陆");
			return;
		}
		//
		if (core.isAlive()) {
			logger.warn("已经登陆在线...");
			return;
		} else if (core.isUseNewVersion() == null) {
			// 初始化版本标记，很重要
			core.setUseNewVersion(this.useNewVersion);
		}
		//
		Boolean result = this.tryToLogin();
		while (result == null) {
			logger.debug("等待扫码登陆确认...");
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				logger.warn("等待扫码登陆确认中断", e.getMessage());
			}
			//
			if (!isRunning) {
				return;
			}
			//
			result = this.tryToLogin();
		}
		//
		if (Boolean.TRUE.equals(result)) {
			logger.info(("登陆成功."));
		} else {
			int tryTimes = 15;
			for (int count = 0; count < tryTimes;) {
				if (!isRunning) {
					return;
				}
				//
				count++;
				//
				logger.debug("获取UUID");
				// 10*6秒
				String uuid = null;
				int uuidTimes = 6;
				while (uuidTimes > 0 && (uuid = this.getUuid(true)) == null) {
					uuidTimes--;
					logger.warn("10秒后重新获取uuid");
					try {
						Thread.sleep(10000);
					} catch (InterruptedException e) {
						logger.warn("获取UUID中断", e.getMessage());
					}
					//
					if (!isRunning) {
						return;
					}
				}
				//
				if (!isRunning) {
					return;
				}
				//
				if (uuid == null) {
					this.doLogout();
					//
					String message = "获取登陆uuid失败，登陆不了，尝试其他方式重新登陆";
					if (this.stateListener != null) {
						this.stateListener.onLoginFail(core.getNodeName(), message);
					}
					logger.error(message);
					return;
				} else {
					if (qrImageDir == null) {
						logger.info("请在浏览器中打开并扫码 " + this.getQrImageUrl(uuid));
						core.setLastMessage("请在浏览器中打开并扫码 ");
						break;
					} else {
						logger.info("获取登陆二维码图片");
						if (this.getAndOpenQrImage(uuid, qrImageDir)) {
							break;
						}
					}
				}
			}
			//
			this.doLogin(qrImageDir);
		}
		//
		if (!isRunning) {
			return;
		}
		//
		logger.debug("清除所有联系人信息和消息列表");
		core.clearAllContactsAndMsgs();

		logger.info("初始化基本信息");
		if (this.initBasicInfo()) {
			// CommonTools.clearScreen();
			logger.info(String.format("欢迎  %s", core.getNickName()));

			logger.info("获取联系人信息");
			this.fetchContacts();

			logger.info("获取群好友及群好友列表");
			this.fetchGroups();

			logger.info("保存最新数据");
			core.saveStoreData();

			core.setLastSyncGroupTs();

			logger.info("开启微信状态通知");
			this.initStatusNotify();

			logger.info("启动心跳和消息监测");
			this.startHeatbeat();

			logger.info("启动数据变动监测");
			this.startDataMonitor();
			//
			readyFlag = true;
		} else {
			logger.warn("初始化基本信息失败");
		}

	}

	// 5 头像缓存目录
	private String headImgCacheDir = null;

	/** 设置头像缓存目录 */
	public boolean setHeadImgCacheDir(String headImgCacheDir) {
		boolean result = true;
		if (headImgCacheDir != null) {
			result = CommonTools.makeDirs(headImgCacheDir);
			if (!result) {
				logger.error("头像缓存目录无法创建");
				return result;
			}
		}
		this.headImgCacheDir = headImgCacheDir;
		//
		return result;
	}

	// 6 无头像的默认替代头像文件名（绝对路径 或 相对于头像缓存目录）
	private File headImgFaultFile = null;

	/** 设置无头像的默认替代头像文件名（绝对路径 或 相对于头像缓存目录，用于 离线获取不到头像 或 未设头像或的场景） */
	public void setHeadImgFaultFileName(String headImgFaultFileName) {
		if (headImgFaultFileName == null) {
			headImgFaultFile = null;
		} else {
			headImgFaultFile = new File(headImgFaultFileName);
			if (!headImgFaultFile.isAbsolute()) {
				if (this.headImgCacheDir == null) {
					headImgFaultFile = null;
					throw new IllegalArgumentException("默认头像文件为相对路径时，必须同时【先指定】头像缓存目录 作为其所在目录！");
				} else {
					headImgFaultFile = new File(this.headImgCacheDir, headImgFaultFileName);
				}
			}
		}
	}

	/** 刷新重新登陆 */
	private static class ActiveReloginThread extends Thread {
		WechatHelper helper;

		public ActiveReloginThread(WechatHelper helper) {
			this.helper = helper;
		}

		@Override
		public void run() {
			helper.stopHeatbeat();
			helper.stopDataMonitor();
			helper.core.setAlive(false);
			helper.readyFlag = false;
			//
			logger.info("开启重新登陆");
			helper.doLogin(helper.lastQrImageDir);
		}

	}

	/**
	 * 获取登陆用的uuid
	 * 
	 * @param refresh
	 *            是否刷新（如果刷新则会触发登陆）
	 * @return
	 */
	private String getUuid(boolean refresh) {
		String retUuid = refresh ? null : core.getUuid();
		if (retUuid == null) {
			// 组装参数和URL
			List<BasicNameValuePair> params = new ArrayList<BasicNameValuePair>();
			BasicNameValuePair wxAppIdParam = core.isUseNewVersion() ? new BasicNameValuePair(UUIDParamEnum.APP_ID_NEW.param(), UUIDParamEnum.APP_ID_NEW.value())
					: new BasicNameValuePair(UUIDParamEnum.APP_ID_OLD.param(), UUIDParamEnum.APP_ID_OLD.value());
			params.add(wxAppIdParam);
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
						if (!waitingForLoginScan) {
							waitingForLoginScan = true;
							triggerWaitingForScanListener(waitingForLoginScan);
						}
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
							if (retUuid == null) {// 状态错乱，等待扫码，但又获取不到uuid
								// 尝试模拟切换设备
								Map<String, Object> loginInfo = core.getLoginInfo();
								loginInfo.put("deviceid", this.createDeviceId());
								core.setLoginInfo(loginInfo);// 同步loginInfo
								core.switchUserAgentType();
							}
							if (!waitingForLoginScan) {
								waitingForLoginScan = true;
								triggerWaitingForScanListener(waitingForLoginScan);
							}
							// 处理扫码刷新登陆需求
							if (core.isAlive()) {
								new ActiveReloginThread(this).start();
							}
						} else if (ResultEnum.WAIT_CONFIRM.getCode().equals(retCode)) {
							message = ResultEnum.WAIT_CONFIRM.getMessage();
							logger.info(message);
							retUuid = core.getUuid();
							if (!waitingForLoginScan) {
								waitingForLoginScan = true;
								triggerWaitingForScanListener(waitingForLoginScan);
							}
						}
					}
				}
			} catch (Exception e) {
				logger.warn("获取登陆uuid失败", e.getMessage());
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
		if (uuid == null) {
			uuid = this.getUuid(true);// 获取新的uuid
		}
		return uuid == null ? null : URLEnum.QRCODE_URL.getUrl() + uuid;
	}

	/**
	 * 获取登陆uuid对应的二维码图片url（或者 已启动登陆，刷新二维码）
	 * 
	 * @param refresh
	 *            是否刷新获取（仅对已启动登陆的有效）
	 * @return
	 */
	public String getQrImageUrl(boolean refresh) {
		if (refresh && this.isRunning && (core.isAlive() || this.tryingToLogin || this.waitingForLoginScan)) {
			return this.getQrImageUrl(null);
		}
		return core.getUuid() == null ? null : this.getQrImageUrl(core.getUuid());
	}

	/**
	 * 获取（最新收到的）登陆uuid对应的二维码图片url
	 */
	public String getQrImageUrlByUuid(String uuid) {
		return uuid == null ? null : this.getQrImageUrl(uuid);
	}

	/**
	 * 获取登陆uuid对应的二维码字节流数据
	 */
	private byte[] getQrImageBytes(String uuid) {
		try {
			String qrUrl = getQrImageUrl(uuid);
			if (qrUrl == null) {
				return null;
			}
			HttpEntity entity = core.getMyHttpClient().doGet(qrUrl, null, false, null);
			byte[] qrImageBytes = EntityUtils.toByteArray(entity);
			if (qrImageBytes == null || qrImageBytes.length < 1) {
				logger.warn("获取的QrImage为空（无效）");
				return null;
			}
			return qrImageBytes;
		} catch (Exception e) {
			logger.warn("请求二维码图片失败", e.getMessage());
			return null;
		}
	}

	/**
	 * 获取并打开登陆二维码（用于测试/调试扫码登陆）
	 */
	private boolean getAndOpenQrImage(String uuid, String qrPathDir) {
		byte[] qrImageBytes = this.getQrImageBytes(uuid);
		if (qrImageBytes == null) {
			return false;
		}
		try {
			CommonTools.makeDirs(qrPathDir);
			//
			File qrFile = new File(qrPathDir, Config.LOGIN_QRCODE_FILE_NAME);
			OutputStream out = new FileOutputStream(qrFile);
			out.write(qrImageBytes);
			out.flush();
			out.close();
			try {
				CommonTools.printQr(qrFile.getAbsolutePath()); // 打开登陆二维码图片
			} catch (Exception e) {
				logger.warn("打开登陆二维码失败", e.getMessage());
			}
			return true;
		} catch (Exception e) {
			logger.warn("获取并打开登陆二维码失败", e.getMessage());
			return false;
		}
	}

	private ReentrantLock tryToLoginLock = new ReentrantLock();
	private volatile boolean tryingToLogin = false;
	private volatile boolean waitingForLoginScan = false;
	private volatile boolean readyFlag = false;

	/** 是否已登陆并就绪（相关信息已获取到了） */
	public boolean isReady() {
		return this.readyFlag;
	}

	/** 延续性登陆uuid（只需在手机端确认即可） */
	private String getPushLoginUuid() {
		try {
			Map<String, Object> loginInfo = core.getLoginInfo();
			String url = (String) loginInfo.get("url");
			String wxuin = (String) loginInfo.get("wxuin");
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
					logger.error(RetCodeEnum.PARAM_ERROR.getMessage());
				} else if (RetCodeEnum.DEVICE_FAIL.equals(retCodeEnum)) {
					logger.error(RetCodeEnum.DEVICE_FAIL.getMessage());
					// 尝试模拟切换设备
					loginInfo.put("deviceid", this.createDeviceId());
					core.setLoginInfo(loginInfo);// 同步loginInfo
					core.switchUserAgentType();
				} else {
					logger.warn(resutJson.getString("msg"));
				}
			}
		} catch (Exception e) {
			logger.warn("推送登陆失败", e.getMessage());
		}
		//
		return null;
	}

	/**
	 * 登陆
	 */
	private Boolean tryToLogin() {
		tryToLoginLock.lock();
		try {
			if (tryingToLogin) {
				return null;
			}
			tryingToLogin = true;
			//
			boolean hasLoggedIn = false;

			int checkInterval = 1000;
			int waitingTimeoutTimes = 0;
			int waitingTimeoutLimit = 5;
			while (!hasLoggedIn && isRunning) {
				try {
					if (!waitingForLoginScan) {
						String uuid = this.getPushLoginUuid();
						if (uuid == null) {
							tryingToLogin = false;
							if (waitingForLoginScan) {
								waitingForLoginScan = false;
								triggerWaitingForScanListener(waitingForLoginScan);
							}
							return false;
						} else {
							logger.info("请在手机上点击 登陆 确认");
							core.setLastMessage("请在手机上点击 登陆 确认");
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
						hasLoggedIn = errMsg == null;
						if (hasLoggedIn) {
							core.setAlive(true);
							if (waitingForLoginScan) {
								waitingForLoginScan = false;
								triggerWaitingForScanListener(waitingForLoginScan);
							}
						} else {
							logger.warn(errMsg);
						}
						break;
					} else if (ResultEnum.WAIT_SCAN.getCode().equals(status)) {
						logger.info(ResultEnum.WAIT_SCAN.getMessage());
						if (!waitingForLoginScan) {
							waitingForLoginScan = true;
							triggerWaitingForScanListener(waitingForLoginScan);
						}
					} else if (ResultEnum.WAIT_CONFIRM.getCode().equals(status)) {
						logger.warn(ResultEnum.WAIT_CONFIRM.getMessage() + (waitingTimeoutTimes > 0 ? "，已超时 " + waitingTimeoutTimes + " 次" : ""));
						if (!waitingForLoginScan) {
							waitingForLoginScan = true;
							triggerWaitingForScanListener(waitingForLoginScan);
						}
					} else if (ResultEnum.WAIT_TIMEOUT.getCode().equals(status)) {
						waitingTimeoutTimes++;
						if (waitingTimeoutTimes < waitingTimeoutLimit) {
							logger.warn(ResultEnum.WAIT_TIMEOUT.getMessage());
							if (!waitingForLoginScan) {
								waitingForLoginScan = true;
								triggerWaitingForScanListener(waitingForLoginScan);
							}
						} else {
							logger.warn(ResultEnum.WAIT_TIMEOUT.getMessage() + " 太长，尝试重新登陆");
							if (waitingForLoginScan) {
								waitingForLoginScan = false;
								triggerWaitingForScanListener(waitingForLoginScan);
								break;
							}
						}
					} else {
						break;
					}
					Thread.sleep(checkInterval);
				} catch (InterruptedException ex) {
					logger.warn("登陆中断", ex.getMessage());
				} catch (Exception e) {
					logger.warn("微信登陆异常！", e);
					break;
				}
			}
			//
			tryingToLogin = false;
			//
			return hasLoggedIn;
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
			logger.debug("------ initBasicInfo ------");
			// logger.debug(result);
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
					readyFlag = false;
					//
					return false;
				}
			}
			JSONObject userSelf = obj.getJSONObject(StorageLoginInfoEnum.User.getKey());
			if (userSelf != null) {
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
				core.setLoginInfo(loginInfo);// 同步loginInfo
				//
				this.filterWxUser(userSelf);
				//
				String userName = userSelf.getString("UserName");
				String nickName = userSelf.getString("NickName");
				String headImgUrl = userSelf.getString("HeadImgUrl");
				//
				core.setUserName(userName);
				core.setNickName(nickName);
				core.setHeadImgUrl(headImgUrl);
				//
				core.setUserSelf(userSelf);
				//
				MsgUser nickSelf = new MsgUser();
				nickSelf.userName = userName;
				nickSelf.nickName = nickName;
				nickSelf.remarkName = userSelf.getString("RemarkName");
				nickSelf.headImgUrl = headImgUrl;
				nickSelf.dispName = userSelf.getString("DisplayName");
				nickSelf.signature = userSelf.getString("Signature");
				nickSelf.userType = MsgUserType.Self.getValue();
				core.setNickSelf(nickSelf);

				// String chatSet = obj.getString("ChatSet");
				// String[] chatSetArray = chatSet.split(",");
				// for (int i = 0; i < chatSetArray.length; i++) {
				// userName = chatSetArray[i].trim();
				// if (userName.startsWith("@@")) {
				// // 更新GroupIdList
				// core.addGroupId(userName);
				// }
				// }
				JSONArray contactListArray = obj.getJSONArray("ContactList");
				for (int i = 0; i < contactListArray.size(); i++) {
					JSONObject member = contactListArray.getJSONObject(i);
					this.addWxMember(member);
				}
				//
				return true;
			} else {
				logger.warn("信息初始化失败");
				return false;
			}
		} catch (Exception e) {
			logger.warn("信息初始化失败", e.getMessage());
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
			// logger.debug("------ wxStatusNotify ------");
			// logger.debug(result);
			//
			return true;
		} catch (Exception e) {
			logger.warn("状态通知接口失败！", e.getMessage());
			return false;
		}
	}

	// 处理头像
	// https://wx.qq.com/cgi-bin/mmwebwx-bin/webwxgetheadimg?seq=693443134&username=@6553b9492ebb0964d6e8b162d2a7321e&skey=
	private static String toFullHeadImgUrl(String srcUrl) {
		if (srcUrl == null) {
			return srcUrl;
		}
		if (srcUrl.startsWith("http")) {
			return srcUrl;
		}
		return URLEnum.WEB_WX_URL.getUrl() + srcUrl;
	}

	private void filterWxUser(JSONObject wxUser) {
		// 拼接url
		String headImgUrl = toFullHeadImgUrl(wxUser.getString("HeadImgUrl"));
		wxUser.put("HeadImgUrl", headImgUrl);
		// 反转emoji（并转&amp; => &）
		String nickName = wxUser.getString("NickName");
		if (nickName != null) {
			nickName = nickName.replace("&amp;", "&");
			nickName = CommonTools.parseEmoji(nickName);
			wxUser.put("NickName", nickName);
		}

		String remarkName = wxUser.getString("RemarkName");
		if (remarkName != null) {
			remarkName = remarkName.replace("&amp;", "&");
			remarkName = CommonTools.parseEmoji(remarkName);
			wxUser.put("RemarkName", remarkName);
		}

		String dispName = wxUser.getString("DisplayName");
		if (dispName != null) {
			dispName = dispName.replace("&amp;", "&");
			dispName = CommonTools.parseEmoji(dispName);
			wxUser.put("DisplayName", dispName);
		}

		String signature = wxUser.getString("Signature");
		if (signature != null) {
			signature = signature.replace("&amp;", "&");
			signature = CommonTools.parseEmoji(signature);
			wxUser.put("Signature", signature);
		}
	}

	private void addWxMember(JSONObject member) {
		this.filterWxUser(member);
		//
		String userName = member.getString("UserName");
		String nickName = member.getString("NickName");
		if (nickName.trim().equals("")) {
			logger.warn("忽略 无 NickName 成员 " + userName);
			return;
		}
		//
		MsgUser msgUser = new MsgUser();
		msgUser.userName = userName;
		msgUser.nickName = nickName;
		msgUser.remarkName = member.getString("RemarkName");
		msgUser.headImgUrl = member.getString("HeadImgUrl");
		msgUser.dispName = member.getString("DisplayName");
		msgUser.signature = member.getString("Signature");
		//
		core.setMember(userName, member);
		int userType = 0;
		if ((member.getInteger("VerifyFlag") & 8) != 0) { // 公众号/服务号
			userType = MsgUserType.Public.getValue();
			core.addPublicUserId(userName);
		} else if (Config.API_SPECIAL_USER.contains(userName)) { // 特殊账号
			userType = MsgUserType.Special.getValue();
			core.addSpecialUserId(userName);
		} else if (userName.startsWith("@@")) { // 群聊
			userType = MsgUserType.Group.getValue();
			core.addGroupId(userName);
			logger.debug("群组：" + nickName);
		} else if (userName.equals(core.getUserName())) { // 自己
			//
		} else { // 普通联系人
			userType = MsgUserType.Friend.getValue();
			core.addContactId(userName);
			logger.debug("联系人：" + nickName);
		}
		//
		msgUser.userType = userType;
		core.setNickNameUser(userType, nickName, msgUser);
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
			}
			CommonTools.filterMsgEmojiEx(retMsg, "Content");
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
				// 微信初始化消息
				logger.debug("---- ----" + MsgTypeValueEnum.MSGTYPE_STATUSNOTIFY.getName());
			} else if (tmpMsgType.equals(MsgTypeValueEnum.MSGTYPE_SYS.getValue())) {// 系统消息
				retMsg.put("Type", MsgTypeCodeEnum.SYS.getCode());
			} else if (tmpMsgType.equals(MsgTypeValueEnum.MSGTYPE_RECALLED.getValue())) { // 撤回消息

			} else {
				logger.debug("无用的消息");
			}
			retMsgList.add(retMsg);
			//
			logger.debug(fromUserName + " => " + toUserName + " 的 " + retMsg.getString("Type") + " 的消息：");
			logger.debug(retMsg.toJSONString());
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
			logger.error("获取好友失败", e.getMessage());
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
			String userName = null;
			for (int i = 0; i < contactList.size(); i++) { // 群好友
				JSONObject member = contactList.getJSONObject(i);
				userName = member.getString("UserName");
				if (userName.startsWith("@@")) { // 群
					this.addWxMember(member);
				}
			}
		} catch (Exception e) {
			logger.error("获取群组失败", e.getMessage());
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
			core.setLoginInfo(loginInfo);// 同步loginInfo
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
				core.setLoginInfo(loginInfo);// 同步loginInfo
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
			logger.warn("同步检查失败", e.getMessage());
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
				core.setLoginInfo(loginInfo);// 同步loginInfo
			}
		} catch (Exception e) {
			logger.warn("拉取同步消息失败", e.getMessage());
		}
		return result;
	}

	/**
	 * 接收消息
	 */
	private Thread heatbeatThead = null;
	private int heatbeatInterval = 1000;

	/** 设置心跳/消息检测 时间间隔（毫秒） */
	public void setHeatbeatInterval(int heatbeatInterval) {
		if (heatbeatInterval < 1000) {
			throw new IllegalArgumentException("心跳/消息检测 时间间隔不能小于 1 秒");
		}
		this.heatbeatInterval = heatbeatInterval;
	}

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
				String message = null;
				while (isRunning) {
					try {
						Thread.sleep(heatbeatInterval);
						//
						if (!core.isAlive()) {
							message = "已掉线";
							break;
						}
						//
						logger.debug("------ ... ------");
						Map<String, String> resultMap = doSyncCheck();
						String retcode = resultMap.get("retcode");
						String selector = resultMap.get("selector");

						if (retcode.equals(RetCodeEnum.UNKOWN.getCode())) {
							logger.warn(RetCodeEnum.UNKOWN.getMessage());
						} else if (retcode.equals(RetCodeEnum.NOT_LOGIN_WARN.getCode())) { // 未登录或已退出
							message = RetCodeEnum.NOT_LOGIN_WARN.getMessage();
							logger.warn(message);
							core.setAlive(false);
							core.setLastMessage(message);
							readyFlag = false;
							break;
						} else if (retcode.equals(RetCodeEnum.LOGIN_OTHERWHERE.getCode())) { // 其它地方登陆
							message = RetCodeEnum.LOGIN_OTHERWHERE.getMessage();
							logger.warn(message);
							core.setAlive(false);
							core.setLastMessage(message);
							readyFlag = false;
							break;
						} else if (retcode.equals(RetCodeEnum.INVALID_COOKIE.getCode())) { // 移动端退出
							message = RetCodeEnum.INVALID_COOKIE.getMessage();
							logger.warn(message);
							core.setAlive(false);
							core.setLastMessage(message);
							readyFlag = false;
							break;
						} else if (retcode.equals(RetCodeEnum.SUCCESS.getCode())) {
							JSONObject msgObj = pullSyncMsgs();
							if (selector.equals("2")) {// 新的消息
								try {
									JSONArray msgList = new JSONArray();
									msgList = msgObj.getJSONArray("AddMsgList");
									msgList = filterWxMsg(msgList);
									if (core.isHandleRecvMsgs()) {
										for (int j = 0; j < msgList.size(); j++) {
											BaseMsg baseMsg = JSON.toJavaObject(msgList.getJSONObject(j), BaseMsg.class);
											core.getRecvMsgList().add(baseMsg);
										}
									} else {
										logger.info("忽略了 " + msgList.size() + " 条收到的消息");
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
											JSONObject member = modContactList.getJSONObject(j);
											addWxMember(member);
										}
									} catch (Exception e) {
										logger.warn(e.getMessage());
									}
								}
							}
						} else {
							pullSyncMsgs();
							logger.debug("------ Other info ------");
							// logger.debug(JSONObject.toJSONString(obj));
						}
					} catch (InterruptedException ex) {
						logger.warn("心跳中断", ex.getMessage());
					} catch (Exception ex) {
						message = "异常：" + ex.getMessage();
						logger.warn(message);
						core.setAlive(false);
						core.setLastMessage(message);
						readyFlag = false;
						break;
					}
				}
				//
				logger.warn("心跳已退出 " + (message == null ? "" : message));
			}
		};
		//
		heatbeatThead.start();
		logger.info("心跳已开启");
	}

	//
	private Thread dataMonitorThead = null;
	private volatile int dataMonitorInterval = 60 * 1000;
	private volatile boolean dataMonitorEnabled = false;

	/** 设置数据监视时间间隔（毫秒） */
	public void setDataMonitorInterval(int dataMonitorInterval) {
		if (dataMonitorInterval < 1000) {
			throw new IllegalArgumentException("数据监视 时间间隔不能小于 1 秒");
		}
		this.dataMonitorInterval = dataMonitorInterval;
	}

	/** 启用/禁用 数据变更监视器 */
	public void setDataMonitorEnabled(boolean dataMonitorEnabled) {
		this.dataMonitorEnabled = dataMonitorEnabled;
	}

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
		if (!dataMonitorEnabled) {
			return;
		}
		//
		this.stopDataMonitor();
		//
		dataMonitorThead = new Thread() {
			public void run() {
				String message = null;
				while (isRunning && dataMonitorEnabled) {
					try {
						Thread.sleep(dataMonitorInterval);
						//
						if (!core.isAlive()) {
							message = "已掉线";
							break;
						}
						//
						logger.debug("------ 010 ------");
						boolean groupSyncFlag = false;
						if (core.hasNoneSyncGroups()) {
							fetchGroups();
							core.setLastSyncGroupTs();
							groupSyncFlag = true;
						}
						if (groupSyncFlag || core.hasDataChanges()) {
							core.saveStoreData();
						}
					} catch (InterruptedException ex) {
						logger.warn("数据监视中断", ex.getMessage());
					} catch (Exception ex) {
						message = "异常：" + ex.getMessage();
						break;
					}
				}
				//
				logger.warn("数据监测已退出 " + (message == null ? "" : message));
			}
		};
		//
		dataMonitorThead.start();
		logger.info("数据监测已开启");
	}

	/** 退出（并清理所有相关数据） */
	public boolean doLogout() {
		boolean result = false;
		//
		this.stopHeatbeat();
		this.stopDataMonitor();
		//
		if (core.isAlive()) {
			// 请求退出登陆
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
				readyFlag = false;
				//
				result = true;
			} catch (Exception e) {
				logger.warn("退出登陆失败", e.getMessage());
			}
			// 重置所有数据
			core.reset();
		} else {
			tryingToLogin = false;
			if (waitingForLoginScan) {
				waitingForLoginScan = false;
				triggerWaitingForScanListener(waitingForLoginScan);
			}
		}
		//
		return result;
	}

	// 数据获取

	public List<JSONObject> getFriendList() {
		return core.getContactList();
	}

	public List<JSONObject> getGroupList() {
		return core.getGroupList();
	}

	// 用户获取
	public MsgUser getNickSelf() {// 用户自己
		return core.getNickSelf();
	}

	public MsgUser getNickFriendUser(String nickName) {// 朋友昵称
		return core.getNickNameUser(MsgUserType.Friend.getValue(), nickName);
	}

	public MsgUser getNickGroupUser(String nickName) {// 群组昵称
		return core.getNickNameUser(MsgUserType.Group.getValue(), nickName);
	}

	//
	public MsgUser getNickNameUser(MsgUserType userType, String nickName) {
		return core.getNickNameUser(userType, nickName);
	}

	public MsgUser getNickNameUser(Integer userType, String nickName) {
		return core.getNickNameUser(userType, nickName);
	}

	private static String makeNickNameUserHeadImgKey(Integer userType, String nickName) {
		return userType + "#-#" + nickName;
	}

	// 头像获取
	public byte[] getNickSelfHeadImgBytes() {// 用户自己
		String nickName = core.getNickName();
		int selfType = MsgUserType.Self.getValue();
		String headImgKey, headImgFileName;
		File headImgFile = null;
		if (this.headImgCacheDir != null) {// 有缓存目录，先到缓存查找
			headImgKey = makeNickNameUserHeadImgKey(selfType, nickName);
			headImgFileName = CommonTools.getMD5Code(headImgKey);
			headImgFile = new File(this.headImgCacheDir, headImgFileName + ".jpg");
			if (headImgFile.exists()) {
				logger.info("从缓存文件读取自己【" + nickName + "】的头像 " + headImgFile.getAbsolutePath());
				return CommonTools.readFileBytes(headImgFile);
			}
		}
		//
		String headImgUrl = core.getHeadImgUrl();
		byte[] imgBytes = this.getHeadImgBytes(headImgUrl);
		if (imgBytes == null) {
			if (headImgFaultFile != null && headImgFaultFile.exists()) {
				logger.info("从默认文件为自己【" + nickName + "】返回头像 " + headImgFaultFile.getAbsolutePath());
				return CommonTools.readFileBytes(headImgFaultFile);
			}
		} else if (this.headImgCacheDir != null) {
			if (!headImgFile.exists() && CommonTools.saveFileBytes(headImgFile, imgBytes)) {
				logger.info("自己【" + nickName + "】的头像 已写入缓存文件 " + headImgFile.getAbsolutePath());
			}
		}
		//
		return imgBytes;
	}

	public byte[] getNickFriendHeadImgBytes(String nickName) {// 朋友昵称
		return this.getNickNameUserHeadImgBytes(MsgUserType.Friend, nickName);
	}

	public byte[] getNickGroupHeadImgBytes(String nickName) {// 群组昵称
		return this.getNickNameUserHeadImgBytes(MsgUserType.Group, nickName);
	}

	public byte[] getNickNameUserHeadImgBytes(MsgUserType userType, String nickName) {
		return this.getNickNameUserHeadImgBytes(userType.getValue(), nickName);
	}

	public byte[] getNickNameUserHeadImgBytes(Integer userType, String nickName) {
		String headImgKey, headImgFileName;
		File headImgFile = null;
		if (this.headImgCacheDir != null) {// 有缓存目录，先到缓存查找
			headImgKey = makeNickNameUserHeadImgKey(userType, nickName);
			headImgFileName = CommonTools.getMD5Code(headImgKey);
			headImgFile = new File(this.headImgCacheDir, headImgFileName + ".jpg");
			if (headImgFile.exists()) {
				logger.info("从缓存文件读取 " + userType + "#" + nickName + " 的头像 " + headImgFile.getAbsolutePath());
				return CommonTools.readFileBytes(headImgFile);
			}
		}
		//
		MsgUser user = core.getNickNameUser(userType, nickName);
		if (user == null) {
			logger.warn("没找到指定类型指定别名的用户信息（可能离线）");
			if (headImgFaultFile != null && headImgFaultFile.exists()) {
				logger.info("从默认文件为 " + userType + "#" + nickName + " 返回头像 " + headImgFaultFile.getAbsolutePath());
				return CommonTools.readFileBytes(headImgFaultFile);
			}
			return null;
		}
		String headImgUrl = user.headImgUrl;
		if (headImgUrl == null || !headImgUrl.startsWith("http")) {
			logger.warn("没有找到用户头像url，可能没有设置头像");
			if (headImgFaultFile != null && headImgFaultFile.exists()) {
				logger.info("从默认文件为 " + userType + "#" + nickName + " 返回头像 " + headImgFaultFile.getAbsolutePath());
				return CommonTools.readFileBytes(headImgFaultFile);
			}
			return null;
		}
		//
		byte[] imgBytes = this.getHeadImgBytes(headImgUrl);
		if (imgBytes == null) {
			if (headImgFaultFile != null && headImgFaultFile.exists()) {
				logger.info("从默认文件为 " + userType + "#" + nickName + " 返回头像 " + headImgFaultFile.getAbsolutePath());
				return CommonTools.readFileBytes(headImgFaultFile);
			}
		} else if (this.headImgCacheDir != null) {
			if (!headImgFile.exists() && CommonTools.saveFileBytes(headImgFile, imgBytes)) {
				logger.info(userType + "#" + nickName + " 的头像 已写入缓存文件 " + headImgFile.getAbsolutePath());
			}
		}
		//
		return imgBytes;
	}

	/**
	 * 获取登陆uuid对应的二维码字节流数据
	 */
	private byte[] getHeadImgBytes(String headImgUrl) {
		if (!core.isAlive()) {
			logger.warn("非登陆态获取不到有效的头像 " + headImgUrl);
			return null;
		}
		//
		try {
			if (headImgUrl == null || !headImgUrl.startsWith("http")) {
				logger.warn("无效头像url " + headImgUrl);
				return null;
			}
			HttpEntity entity = core.getMyHttpClient().doGet(headImgUrl, null, false, null);
			byte[] headImgBytes = EntityUtils.toByteArray(entity);
			if (headImgBytes == null || headImgBytes.length < 1) {
				logger.warn("获取的头像为空（无效）");
				return null;
			}
			//
			return headImgBytes;
		} catch (Exception e) {
			logger.warn("获取的头像失败", e);
			return null;
		}
	}
}
