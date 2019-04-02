package cn.open.itchat4j;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import cn.open.itchat4j.core.CoreStateListener;
import cn.open.itchat4j.core.FileDataStore;
import cn.open.itchat4j.core.MsgCenter;
import cn.open.itchat4j.core.MsgHandler;

public class Wechat {
	private static final Logger logger = LoggerFactory.getLogger(Wechat.class);
	//
	private WechatHelper wechatHelper = WechatHelper.getInstance();
	private MsgHandler msgHandler;

	public Wechat(MsgHandler msgHandler) {
		this.msgHandler = msgHandler;
	}

	public void start(String qrImageFileDir) {
		this.start(qrImageFileDir, null, null, null);
	}

	public void start(String qrImageFileDir, String dataStoreFilePath) {
		this.start(dataStoreFilePath, qrImageFileDir, null, null);
	}

	public void start(String qrImageFileDir, String dataStoreFilePath, String headImgCacheDir) {
		this.start(dataStoreFilePath, qrImageFileDir, headImgCacheDir, null);
	}

	public void start(String qrImageFileDir, String dataStoreFilePath, String headImgCacheDir, String headImgFaultFileName) {
		// 消息处理线程
		Thread msgThread = MsgCenter.handleMsgs(msgHandler, false);
		// 状态监听示例
		CoreStateListener stateListener = new CoreStateListener() {

			@Override
			public void onUserOnline(String nodeName) {
				logger.info("微信在本节点  " + nodeName + " 上线");
				// TODO 发送广播消息(nodeName, online, true)
			}

			@Override
			public void onUserReady(String nodeName) {
				logger.info("微信在本节点  " + nodeName + " 就绪");
				// TODO 发送广播消息(nodeName, ready, true)
			}

			@Override
			public void onUserOffline(String nodeName) {
				logger.info("微信从本节点 " + nodeName + " 下线");
				// TODO 发送广播消息(nodeName, offline, true)

				if (!wechatHelper.isWaitingForLoginScan()) {// 扫码重新登陆的离线不停止
					// 下面仅仅是演示用（一般在应用停止时才会执行）
					msgThread.interrupt();
					//
					wechatHelper.shutdown();
				}
			}

			@Override
			public void onDataChanged(String nodeName, long dataVersion) {
				Date dataDate = new Date(dataVersion);
				SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
				logger.info("数据在本节点 " + nodeName + " 的 " + formatter.format(dataDate) + " 发生更改");
				// TODO 发送广播消息(nodeName, data-changed)
			}

			@Override
			public void onUuidRefreshed(String nodeName, String uuid) {
				logger.info("微信在本节点 " + nodeName + " 刷新了uuid : " + uuid);
				logger.info(wechatHelper.getQrImageUrlByUuid(uuid));
				// TODO 发送广播消息(nodeName, uuid-changed, uuid)
			}

			@Override
			public void onWaitForScan(String nodeName, boolean waiting) {
				logger.info("微信在本节点 " + nodeName + " 等着扫码登陆：");
				// TODO 发送广播消息(nodeName, waiting, waiting?)

				// 下面仅仅是演示用
				if (waiting) {
					logger.info("正在等着扫码（不要再调登陆了），或打开如下url扫码登陆：");
					logger.info(wechatHelper.getQrImageUrl(false));
				}
			}

		};
		// wechatHelper.setUseNewVersion(true);
		// 1
		wechatHelper.setNodeName("demoNode");
		// 设置头像缓存目录
		wechatHelper.setHeadImgCacheDir(headImgCacheDir);
		// 设置无头像的默认替代头像文件名（绝对路径 或 相对于头像缓存目录，用于 离线获取不到头像 或 未设头像或的场景）
		wechatHelper.setHeadImgFaultFileName(headImgFaultFileName);
		// 2
		if (dataStoreFilePath == null) {
			wechatHelper.initCore(stateListener); // 默认使用MemDataStore
		} else {
			FileDataStore dataStore = new FileDataStore(dataStoreFilePath);
			wechatHelper.initCore(dataStore, stateListener);
		}
		// 3
		wechatHelper.startup();
		// 4
		wechatHelper.doLogin(qrImageFileDir);

		//
		logger.info("+++++++++++++++++++开始消息处理+++++++++++++++++++++");
		msgThread.start();

		// 等待登陆上线就绪
		while (!wechatHelper.isReady()) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				logger.warn("可能已强制结束");
				System.exit(-1);
			}
		}

		// 等待一段时间
		try {
			Thread.sleep(4000);
		} catch (InterruptedException e) {
			logger.warn("可能已强制结束");
			System.exit(-1);
		}

		logger.info(" -------- 获取 信息及头像 -------");
		logger.info(JSON.toJSONString(wechatHelper.getNickSelf(), true));
		wechatHelper.getNickSelfHeadImgBytes();

		logger.info(" -------- 获取 好友 信息及头像 -------");
		List<JSONObject> wxUsers = wechatHelper.getFriendList();
		for (int i = 0; i < wxUsers.size(); i++) {
			JSONObject wxUser = wxUsers.get(i);
			String nickName = wxUser.getString("NickName");
			logger.info(JSON.toJSONString(wechatHelper.getNickFriendUser(nickName), true));
			wechatHelper.getNickFriendHeadImgBytes(nickName);
		}

		logger.info(" -------- 获取 群组 信息及头像 -------");
		wxUsers = wechatHelper.getGroupList();
		for (int i = 0; i < wxUsers.size(); i++) {
			JSONObject wxUser = wxUsers.get(i);
			String nickName = wxUser.getString("NickName");
			logger.info(JSON.toJSONString(wechatHelper.getNickGroupUser(nickName), true));
			wechatHelper.getNickGroupHeadImgBytes(nickName);
		}

		// 模拟登陆态刷新二维码（从而除服重新登陆）
		// wechatHelper.getQrImageUrl(true);

		// // 模拟消息发送
		// String nickName = "😀ོ ꧁灬尼莫灬꧂";
		// MsgUser user = wechatHelper.getNickNameUser(MsgUserType.Friend, nickName);
		// logger.info(JSON.toJSONString(user));
		// MsgHelper.sendTextMsgByNickName(MsgUserType.Friend, nickName, "这是从我的微信模拟客户端发出的消息");
	}

}
