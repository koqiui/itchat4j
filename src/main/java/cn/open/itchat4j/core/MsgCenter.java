package cn.open.itchat4j.core;

import java.util.Queue;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.open.itchat4j.beans.BaseMsg;
import cn.open.itchat4j.enums.MsgTypeCodeEnum;

/**
 * 消息处理中心
 * 
 * @author https://github.com/yaphone
 * @date 创建时间：2017年5月14日 下午12:47:50
 * @version 1.0
 *
 */
public class MsgCenter {
	private static Logger logger = LoggerFactory.getLogger(MsgCenter.class);

	private static Core core = Core.getInstance();

	private static int sendDelayMs = 1000;

	public static Thread handleMsgs(MsgHandler msgHandler) {
		return handleMsgs(msgHandler, true);
	}

	/**
	 * 消息处理
	 * 
	 * @author https://github.com/yaphone
	 * @date 2017年5月14日 上午10:52:34
	 * @param msgHandler
	 */
	public static Thread handleMsgs(MsgHandler msgHandler, boolean autoStart) {
		Thread theThread = new Thread() {
			@Override
			public void run() {
				Queue<BaseMsg> recvMsgList = core.getRecvMsgList();
				while (true) {
					BaseMsg msg = recvMsgList.poll();
					if (msg != null && msg.getContent() != null) {
						if (msg.getContent().length() > 0) {
							if (msg.getTypeCode() != null) {
								try {
									if (msg.getTypeCode().equals(MsgTypeCodeEnum.TEXT.getCode())) {
										String result = msgHandler.textMsgHandle(msg);
										MsgHelper.sendTextMsg(msg.getFromUserName(), result);
									} else if (msg.getTypeCode().equals(MsgTypeCodeEnum.PIC.getCode())) {
										String result = msgHandler.picMsgHandle(msg);
										MsgHelper.sendTextMsg(msg.getFromUserName(), result);
									} else if (msg.getTypeCode().equals(MsgTypeCodeEnum.VOICE.getCode())) {
										String result = msgHandler.voiceMsgHandle(msg);
										MsgHelper.sendTextMsg(msg.getFromUserName(), result);
									} else if (msg.getTypeCode().equals(MsgTypeCodeEnum.VIEDO.getCode())) {
										String result = msgHandler.viedoMsgHandle(msg);
										MsgHelper.sendTextMsg(msg.getFromUserName(), result);
									} else if (msg.getTypeCode().equals(MsgTypeCodeEnum.NAMECARD.getCode())) {
										String result = msgHandler.nameCardMsgHandle(msg);
										MsgHelper.sendTextMsg(msg.getFromUserName(), result);
									} else if (msg.getTypeCode().equals(MsgTypeCodeEnum.SYS.getCode())) {
										// 系统消息
										msgHandler.sysMsgHandle(msg);
									} else if (msg.getTypeCode().equals(MsgTypeCodeEnum.VERIFYMSG.getCode())) { // 确认添加好友消息
										String result = msgHandler.verifyAddFriendMsgHandle(msg);
										MsgHelper.sendTextMsg(msg.getRecommendInfo().getUserName(), result);
									} else if (msg.getTypeCode().equals(MsgTypeCodeEnum.MEDIA.getCode())) { // 多媒体消息
										String result = msgHandler.mediaMsgHandle(msg);
										MsgHelper.sendTextMsg(msg.getFromUserName(), result);
									}
								} catch (Exception e) {
									logger.error(e.getMessage());
								}
							}
						}
					}
					try {
						TimeUnit.MILLISECONDS.sleep(sendDelayMs);
					} catch (InterruptedException e) {
						break;
					}
				}
				//
				logger.warn("已中止消息处理循环");
			}

		};
		//
		if (autoStart) {
			theThread.start();
		}
		//
		return theThread;
	}

}
