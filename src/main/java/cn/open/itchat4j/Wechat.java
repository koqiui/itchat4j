package cn.open.itchat4j;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

	public void start(String dataStoreFilePath, String qrImageFileDir) {
		if (dataStoreFilePath == null) {
			wechatHelper.initCore(); // 默认使用MemDataStore
		} else {
			FileDataStore dataStore = new FileDataStore(dataStoreFilePath);
			wechatHelper.initCore(dataStore);
		}
		//
		wechatHelper.doLogin(qrImageFileDir);
		//
		logger.info("+++++++++++++++++++开始消息处理+++++++++++++++++++++");
		new Thread(new Runnable() {
			@Override
			public void run() {
				MsgCenter.handleMsg(msgHandler);
			}
		}).start();

	}

}
