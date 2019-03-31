package cn.open.itchat4j;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.open.itchat4j.controller.LoginController;
import cn.open.itchat4j.core.MsgCenter;
import cn.open.itchat4j.core.MsgHandler;

public class Wechat {
	private static final Logger logger = LoggerFactory.getLogger(Wechat.class);
	private MsgHandler msgHandler;

	public Wechat(MsgHandler msgHandler, String qrPath) {

		this.msgHandler = msgHandler;

		// 登陆
		LoginController login = new LoginController();
		login.login(qrPath);
	}

	public void start() {
		logger.info("+++++++++++++++++++开始消息处理+++++++++++++++++++++");
		new Thread(new Runnable() {
			@Override
			public void run() {
				MsgCenter.handleMsg(msgHandler);
			}
		}).start();
		
		try {
			Thread.sleep(20000);
			
			WechatHelper.getInstance().shutdown();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

}
