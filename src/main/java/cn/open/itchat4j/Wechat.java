package cn.open.itchat4j;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSON;

import cn.open.itchat4j.core.CoreStateListener;
import cn.open.itchat4j.core.FileDataStore;
import cn.open.itchat4j.core.MsgCenter;
import cn.open.itchat4j.core.MsgHandler;
import cn.open.itchat4j.core.MsgHelper;
import cn.open.itchat4j.core.MsgUser;
import cn.open.itchat4j.enums.MsgUserType;

public class Wechat {
	private static final Logger logger = LoggerFactory.getLogger(Wechat.class);
	//
	private WechatHelper wechatHelper = WechatHelper.getInstance();
	private MsgHandler msgHandler;

	public Wechat(MsgHandler msgHandler) {
		this.msgHandler = msgHandler;
	}

	public void start(String dataStoreFilePath, String qrImageFileDir) {
		// 消息处理线程
		Thread msgThread = MsgCenter.handleMsgs(msgHandler, false);
		// 状态监听示例
		CoreStateListener stateListener = new CoreStateListener() {

			@Override
			public void onUserOnline(String nodeName) {
				logger.info("微信已在 " + nodeName + " 上线");
				// TODO 在中央缓存更新节点在线状态
			}

			@Override
			public void onUserOffline(String nodeName) {
				logger.info("微信已从 " + nodeName + " 下线");
				// TODO 在中央缓存更新节点在线状态

				// 下面仅仅是演示用（一般在应用停止时执行）
				msgThread.interrupt();
				//
				wechatHelper.shutdown();
			}

			@Override
			public void onDataChanged(long dataVersion) {
				Date dataDate = new Date(dataVersion);
				SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
				logger.info("数据在  " + formatter.format(dataDate) + " 更改");
				// TODO
			}

			@Override
			public void onUuidRefreshed() {
				logger.info("微信已刷新uuid已刷新，可打开如下url扫码登陆：");
				logger.info(wechatHelper.getQrImageUrl(false));
				// TODO 在中央缓存更新扫码url
			}

			@Override
			public void onWaitForScan(boolean waiting) {
				if (waiting) {
					logger.info("正在等着扫码（不要再调登陆了），或打开如下url扫码登陆：");
					logger.info(wechatHelper.getQrImageUrl(false));
					// TODO 在中央缓存更新扫码url
				} else {
					// TODO 在中央缓存清除扫码url
				}
			}
		};
		// 1
		if (dataStoreFilePath == null) {
			wechatHelper.initCore(); // 默认使用MemDataStore
		} else {
			FileDataStore dataStore = new FileDataStore(dataStoreFilePath);
			wechatHelper.initCore(dataStore, stateListener);
		}
		// 2
		wechatHelper.setNodeName("demoNode");
		// 3
		wechatHelper.startup();
		// 4
		wechatHelper.doLogin(qrImageFileDir);
		//
		logger.info("+++++++++++++++++++开始消息处理+++++++++++++++++++++");
		msgThread.start();

		// 等待登陆上线
		while (!wechatHelper.isAlive()) {
			try {
				Thread.currentThread().sleep(1000);
			} catch (InterruptedException e) {
				logger.warn("可能已强制结束");
				System.exit(-1);
			}
		}
		// 模拟消息发送
		String nickName = "😀ོ ꧁灬尼莫灬꧂";
		MsgUser user = wechatHelper.getNickNameUser(MsgUserType.Friend, nickName);
		logger.info(JSON.toJSONString(user));
		MsgHelper.sendTextMsgByNickName(MsgUserType.Friend, nickName, "这是从我的微信模拟客户端发出的消息");
	}

}
