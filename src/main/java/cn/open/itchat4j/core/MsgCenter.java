package cn.open.itchat4j.core;

import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import cn.open.itchat4j.api.MessageTools;
import cn.open.itchat4j.beans.BaseMsg;
import cn.open.itchat4j.enums.MsgTypeCodeEnum;
import cn.open.itchat4j.enums.MsgTypeValueEnum;
import cn.open.itchat4j.tools.CommonTools;

/**
 * 消息处理中心
 * 
 * @author https://github.com/yaphone
 * @date 创建时间：2017年5月14日 下午12:47:50
 * @version 1.0
 *
 */
public class MsgCenter {
	private static Logger LOG = LoggerFactory.getLogger(MsgCenter.class);

	private static Core core = Core.getInstance();

	private static int sendDelayMs = 1000;

	/**
	 * 接收消息，放入队列
	 * 
	 * @author https://github.com/yaphone
	 * @date 2017年4月23日 下午2:30:48
	 * @param msgList
	 * @return
	 */
	public static JSONArray produceMsg(JSONArray msgList) {
		JSONArray retMsgList = new JSONArray();
		for (int i = 0; i < msgList.size(); i++) {
			JSONObject tmpMsg = new JSONObject();
			JSONObject retMsg = msgList.getJSONObject(i);
			retMsg.put("groupMsg", false);// 是否是群消息
			String fromUserName = retMsg.getString("FromUserName");
			String toUserName = retMsg.getString("ToUserName");
			//
			if (fromUserName.contains("@@") || toUserName.contains("@@")) { // 群聊消息
				if (fromUserName.contains("@@") && !core.getGroupIdList().contains(fromUserName)) {
					core.getGroupIdList().add(fromUserName);
				} else if (toUserName.contains("@@") && !core.getGroupIdList().contains(toUserName)) {
					core.getGroupIdList().add(toUserName);
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
				// MessageTools.addFriend(core, userName, 3, ticket); // 确认添加好友
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
				LOG.info("---- ----" + MsgTypeValueEnum.MSGTYPE_STATUSNOTIFY.getName());
				LOG.info("");

			} else if (tmpMsgType.equals(MsgTypeValueEnum.MSGTYPE_SYS.getValue())) {// 系统消息
				retMsg.put("Type", MsgTypeCodeEnum.SYS.getCode());
			} else if (tmpMsgType.equals(MsgTypeValueEnum.MSGTYPE_RECALLED.getValue())) { // 撤回消息

			} else {
				LOG.info("Useless msg");
			}
			retMsgList.add(retMsg);
			//
			LOG.info("收到一条来自 " + fromUserName + " 的 " + retMsg.getString("Type") + " 消息：");
			LOG.info(retMsg.toJSONString());
		}
		return retMsgList;
	}

	/**
	 * 消息处理
	 * 
	 * @author https://github.com/yaphone
	 * @date 2017年5月14日 上午10:52:34
	 * @param msgHandler
	 */
	public static void handleMsg(MsgHandler msgHandler) {
		Queue<BaseMsg> msgList = core.getMsgList();
		while (true) {
			BaseMsg msg = msgList.poll();
			if (msg != null && msg.getContent() != null) {
				if (msg.getContent().length() > 0) {
					if (msg.getTypeCode() != null) {
						try {
							if (msg.getTypeCode().equals(MsgTypeCodeEnum.TEXT.getCode())) {
								String result = msgHandler.textMsgHandle(msg);
								MessageTools.sendMsgById(result, msg.getFromUserName());
							} else if (msg.getTypeCode().equals(MsgTypeCodeEnum.PIC.getCode())) {
								String result = msgHandler.picMsgHandle(msg);
								MessageTools.sendMsgById(result, msg.getFromUserName());
							} else if (msg.getTypeCode().equals(MsgTypeCodeEnum.VOICE.getCode())) {
								String result = msgHandler.voiceMsgHandle(msg);
								MessageTools.sendMsgById(result, msg.getFromUserName());
							} else if (msg.getTypeCode().equals(MsgTypeCodeEnum.VIEDO.getCode())) {
								String result = msgHandler.viedoMsgHandle(msg);
								MessageTools.sendMsgById(result, msg.getFromUserName());
							} else if (msg.getTypeCode().equals(MsgTypeCodeEnum.NAMECARD.getCode())) {
								String result = msgHandler.nameCardMsgHandle(msg);
								MessageTools.sendMsgById(result, msg.getFromUserName());
							} else if (msg.getTypeCode().equals(MsgTypeCodeEnum.SYS.getCode())) {
								// 系统消息
								msgHandler.sysMsgHandle(msg);
							} else if (msg.getTypeCode().equals(MsgTypeCodeEnum.VERIFYMSG.getCode())) { // 确认添加好友消息
								String result = msgHandler.verifyAddFriendMsgHandle(msg);
								MessageTools.sendMsgById(result, msg.getRecommendInfo().getUserName());
							} else if (msg.getTypeCode().equals(MsgTypeCodeEnum.MEDIA.getCode())) { // 多媒体消息
								String result = msgHandler.mediaMsgHandle(msg);
								MessageTools.sendMsgById(result, msg.getFromUserName());
							}
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}
			}
			try {
				TimeUnit.MILLISECONDS.sleep(sendDelayMs);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

}
