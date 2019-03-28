package cn.open.itchat4j.core;

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
import cn.open.itchat4j.face.IMsgHandlerFace;
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

	/**
	 * 接收消息，放入队列
	 * 
	 * @author https://github.com/yaphone
	 * @date 2017年4月23日 下午2:30:48
	 * @param msgList
	 * @return
	 */
	public static JSONArray produceMsg(JSONArray msgList) {
		JSONArray result = new JSONArray();
		for (int i = 0; i < msgList.size(); i++) {
			JSONObject msg = new JSONObject();
			JSONObject m = msgList.getJSONObject(i);
			m.put("groupMsg", false);// 是否是群消息
			if (m.getString("FromUserName").contains("@@") || m.getString("ToUserName").contains("@@")) { // 群聊消息
				if (m.getString("FromUserName").contains("@@") && !core.getGroupIdList().contains(m.getString("FromUserName"))) {
					core.getGroupIdList().add((m.getString("FromUserName")));
				} else if (m.getString("ToUserName").contains("@@") && !core.getGroupIdList().contains(m.getString("ToUserName"))) {
					core.getGroupIdList().add((m.getString("ToUserName")));
				}
				// 群消息与普通消息不同的是在其消息体（Content）中会包含发送者id及":<br/>"消息，这里需要处理一下，去掉多余信息，只保留消息内容
				if (m.getString("Content").contains("<br/>")) {
					String content = m.getString("Content").substring(m.getString("Content").indexOf("<br/>") + 5);
					m.put("Content", content);
					m.put("groupMsg", true);
				}
			} else {
				CommonTools.msgFormatter(m, "Content");
			}
			if (m.getInteger("MsgType").equals(MsgTypeValueEnum.MSGTYPE_TEXT.getValue())) { // words
																						// 文本消息
				if (m.getString("Url").length() != 0) {
					String regEx = "(.+?\\(.+?\\))";
					Matcher matcher = CommonTools.getMatcher(regEx, m.getString("Content"));
					String data = "Map";
					if (matcher.find()) {
						data = matcher.group(1);
					}
					msg.put("Type", "Map");
					msg.put("Text", data);
				} else {
					msg.put("Type", MsgTypeCodeEnum.TEXT.getCode());
					msg.put("Text", m.getString("Content"));
				}
				m.put("Type", msg.getString("Type"));
				m.put("Text", msg.getString("Text"));
			} else if (m.getInteger("MsgType").equals(MsgTypeValueEnum.MSGTYPE_IMAGE.getValue()) || m.getInteger("MsgType").equals(MsgTypeValueEnum.MSGTYPE_EMOTICON.getValue())) { // 图片消息
				m.put("Type", MsgTypeCodeEnum.PIC.getCode());
			} else if (m.getInteger("MsgType").equals(MsgTypeValueEnum.MSGTYPE_VOICE.getValue())) { // 语音消息
				m.put("Type", MsgTypeCodeEnum.VOICE.getCode());
			} else if (m.getInteger("MsgType").equals(MsgTypeValueEnum.MSGTYPE_VERIFYMSG.getValue())) {// friends
				// 好友确认消息
				// MessageTools.addFriend(core, userName, 3, ticket); // 确认添加好友
				m.put("Type", MsgTypeCodeEnum.VERIFYMSG.getCode());

			} else if (m.getInteger("MsgType").equals(MsgTypeValueEnum.MSGTYPE_SHARECARD.getValue())) { // 共享名片
				m.put("Type", MsgTypeCodeEnum.NAMECARD.getCode());

			} else if (m.getInteger("MsgType").equals(MsgTypeValueEnum.MSGTYPE_VIDEO.getValue()) || m.getInteger("MsgType").equals(MsgTypeValueEnum.MSGTYPE_MICROVIDEO.getValue())) {// viedo
				m.put("Type", MsgTypeCodeEnum.VIEDO.getCode());
			} else if (m.getInteger("MsgType").equals(MsgTypeValueEnum.MSGTYPE_MEDIA.getValue())) { // 多媒体消息
				m.put("Type", MsgTypeCodeEnum.MEDIA.getCode());
			} else if (m.getInteger("MsgType").equals(MsgTypeValueEnum.MSGTYPE_STATUSNOTIFY.getValue())) {// phone
				// init
				// 微信初始化消息

			} else if (m.getInteger("MsgType").equals(MsgTypeValueEnum.MSGTYPE_SYS.getValue())) {// 系统消息
				m.put("Type", MsgTypeCodeEnum.SYS.getCode());
			} else if (m.getInteger("MsgType").equals(MsgTypeValueEnum.MSGTYPE_RECALLED.getValue())) { // 撤回消息

			} else {
				LOG.info("Useless msg");
			}
			LOG.info("收到消息一条，来自: " + m.getString("FromUserName"));
			result.add(m);
		}
		return result;
	}

	/**
	 * 消息处理
	 * 
	 * @author https://github.com/yaphone
	 * @date 2017年5月14日 上午10:52:34
	 * @param msgHandler
	 */
	public static void handleMsg(IMsgHandlerFace msgHandler) {
		while (true) {
			if (core.getMsgList().size() > 0 && core.getMsgList().get(0).getContent() != null) {
				if (core.getMsgList().get(0).getContent().length() > 0) {
					BaseMsg msg = core.getMsgList().get(0);
					if (msg.getTypeCode() != null) {
						try {
							if (msg.getTypeCode().equals(MsgTypeCodeEnum.TEXT.getCode())) {
								String result = msgHandler.textMsgHandle(msg);
								MessageTools.sendMsgById(result, core.getMsgList().get(0).getFromUserName());
							} else if (msg.getTypeCode().equals(MsgTypeCodeEnum.PIC.getCode())) {

								String result = msgHandler.picMsgHandle(msg);
								MessageTools.sendMsgById(result, core.getMsgList().get(0).getFromUserName());
							} else if (msg.getTypeCode().equals(MsgTypeCodeEnum.VOICE.getCode())) {
								String result = msgHandler.voiceMsgHandle(msg);
								MessageTools.sendMsgById(result, core.getMsgList().get(0).getFromUserName());
							} else if (msg.getTypeCode().equals(MsgTypeCodeEnum.VIEDO.getCode())) {
								String result = msgHandler.viedoMsgHandle(msg);
								MessageTools.sendMsgById(result, core.getMsgList().get(0).getFromUserName());
							} else if (msg.getTypeCode().equals(MsgTypeCodeEnum.NAMECARD.getCode())) {
								String result = msgHandler.nameCardMsgHandle(msg);
								MessageTools.sendMsgById(result, core.getMsgList().get(0).getFromUserName());
							} else if (msg.getTypeCode().equals(MsgTypeCodeEnum.SYS.getCode())) { // 系统消息
								msgHandler.sysMsgHandle(msg);
							} else if (msg.getTypeCode().equals(MsgTypeCodeEnum.VERIFYMSG.getCode())) { // 确认添加好友消息
								String result = msgHandler.verifyAddFriendMsgHandle(msg);
								MessageTools.sendMsgById(result, core.getMsgList().get(0).getRecommendInfo().getUserName());
							} else if (msg.getTypeCode().equals(MsgTypeCodeEnum.MEDIA.getCode())) { // 多媒体消息
								String result = msgHandler.mediaMsgHandle(msg);
								MessageTools.sendMsgById(result, core.getMsgList().get(0).getFromUserName());
							}
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}
				core.getMsgList().remove(0);
			}
			try {
				TimeUnit.MILLISECONDS.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

}
