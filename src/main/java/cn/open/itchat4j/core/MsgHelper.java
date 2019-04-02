package cn.open.itchat4j.core;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.activation.MimetypesFileTypeMap;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.Consts;
import org.apache.http.HttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import cn.open.itchat4j.beans.BaseMsg;
import cn.open.itchat4j.beans.RecommendInfo;
import cn.open.itchat4j.enums.MsgTypeValueEnum;
import cn.open.itchat4j.enums.MsgUserType;
import cn.open.itchat4j.enums.StorageLoginInfoEnum;
import cn.open.itchat4j.enums.URLEnum;
import cn.open.itchat4j.enums.VerifyFriendEnum;
import cn.open.itchat4j.utils.Config;

/**
 * 消息处理类
 * 
 * @author https://github.com/yaphone
 * @date 创建时间：2017年4月23日 下午2:30:37
 * @version 1.0
 *
 */
public class MsgHelper {
	private static Logger logger = LoggerFactory.getLogger(MsgHelper.class);
	private static Core core = Core.getInstance();

	private static String getUserNameByNickName(MsgUserType userType, String nickName) {
		return core.getNickUserName(userType, nickName);
	}

	/**
	 * 根据UserName发送文本消息
	 * 
	 * @author https://github.com/yaphone
	 * @date 2017年5月4日 下午11:17:38
	 * @param userName
	 * @param text
	 */
	public static boolean sendTextMsg(String userName, String text) {
		if (text == null) {
			return false;
		}
		return sendTypedMsg(MsgTypeValueEnum.MSGTYPE_TEXT.getValue(), userName, text);
	}

	public static boolean sendTextMsgByFriendNickName(String nickName, String text) {
		return sendTextMsgByNickName(MsgUserType.Friend, nickName, text);
	}

	public static boolean sendTextMsgByGroupNickName(String nickName, String text) {
		return sendTextMsgByNickName(MsgUserType.Group, nickName, text);
	}

	public static boolean sendTextMsgByNickName(MsgUserType userType, String nickName, String text) {
		String userName = getUserNameByNickName(userType, nickName);
		if (userName == null) {
			logger.warn("没有找到给定类型和别名的用户");
			return false;
		}
		return sendTextMsg(userName, text);
	}

	/**
	 * 消息发送
	 * 
	 * @author https://github.com/yaphone
	 * @date 2017年4月23日 下午2:32:02
	 * @param msgType
	 * @param userName
	 * @param content
	 */
	public static boolean sendTypedMsg(int msgType, String userName, String content) {
		if (!core.isAlive()) {
			logger.warn("微信已离线，消息发送已取消");
			return false;
		}
		//
		logger.info(String.format("发送消息 %s: %s", userName, content));
		//
		Map<String, Object> loginInfo = core.getLoginInfo();
		String url = String.format(URLEnum.WEB_WX_SEND_MSG.getUrl(), loginInfo.get("url"));
		Map<String, Object> msgMap = new HashMap<String, Object>();
		msgMap.put("Type", msgType);
		msgMap.put("Content", content);
		msgMap.put("FromUserName", core.getUserName());
		msgMap.put("ToUserName", userName == null ? core.getUserName() : userName);
		msgMap.put("LocalID", new Date().getTime() * 10);
		msgMap.put("ClientMsgId", new Date().getTime() * 10);
		Map<String, Object> paramMap = core.newParamMap();
		paramMap.put("Msg", msgMap);
		paramMap.put("Scene", 0);
		try {
			String paramStr = JSON.toJSONString(paramMap);
			HttpEntity entity = core.getMyHttpClient().doPost(url, paramStr);
			EntityUtils.toString(entity, Consts.UTF_8);
			//
			return true;
		} catch (Exception e) {
			logger.error("webWxSendMsg", e);
			//
			return false;
		}
	}

	/**
	 * 上传多媒体文件到 微信服务器，目前应该支持3种类型: 1. pic 直接显示，包含图片，表情 2.video 3.doc 显示为文件，包含PDF等
	 * 
	 * @author https://github.com/yaphone
	 * @date 2017年5月7日 上午12:41:13
	 * @param filePath
	 * @return
	 */
	private static JSONObject uploadMediaFile(String filePath) {
		if (!core.isAlive()) {
			logger.warn("微信已离线，消息发送已取消");
		}
		//
		File f = new File(filePath);
		if (!f.exists() && f.isFile()) {
			logger.info("file is not exist");
			return null;
		}
		Map<String, Object> loginInfo = core.getLoginInfo();
		String url = String.format(URLEnum.WEB_WX_UPLOAD_MEDIA.getUrl(), loginInfo.get("fileUrl"));
		String mimeType = new MimetypesFileTypeMap().getContentType(f);
		String mediaType = "";
		if (mimeType == null) {
			mimeType = "text/plain";
		} else {
			mediaType = mimeType.split("/")[0].equals("image") ? "pic" : "doc";
		}
		String lastModifieDate = new SimpleDateFormat("yyyy MM dd HH:mm:ss").format(new Date());
		long fileSize = f.length();
		String passTicket = (String) loginInfo.get("pass_ticket");
		String clientMediaId = String.valueOf(new Date().getTime()) + String.valueOf(new Random().nextLong()).substring(0, 4);
		String webwxDataTicket = core.getMyHttpClient().getCookie("webwx_data_ticket");
		if (webwxDataTicket == null) {
			logger.error("get cookie webwx_data_ticket error");
			return null;
		}

		Map<String, Object> paramMap = core.newParamMap();

		paramMap.put("ClientMediaId", clientMediaId);
		paramMap.put("TotalLen", fileSize);
		paramMap.put("StartPos", 0);
		paramMap.put("DataLen", fileSize);
		paramMap.put("MediaType", 4);

		MultipartEntityBuilder builder = MultipartEntityBuilder.create();
		builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);

		builder.addTextBody("id", "WU_FILE_0", ContentType.TEXT_PLAIN);
		builder.addTextBody("name", filePath, ContentType.TEXT_PLAIN);
		builder.addTextBody("type", mimeType, ContentType.TEXT_PLAIN);
		builder.addTextBody("lastModifieDate", lastModifieDate, ContentType.TEXT_PLAIN);
		builder.addTextBody("size", String.valueOf(fileSize), ContentType.TEXT_PLAIN);
		builder.addTextBody("mediatype", mediaType, ContentType.TEXT_PLAIN);
		builder.addTextBody("uploadmediarequest", JSON.toJSONString(paramMap), ContentType.TEXT_PLAIN);
		builder.addTextBody("webwx_data_ticket", webwxDataTicket, ContentType.TEXT_PLAIN);
		builder.addTextBody("pass_ticket", passTicket, ContentType.TEXT_PLAIN);
		builder.addBinaryBody("filename", f, ContentType.create(mimeType), filePath);
		HttpEntity reqEntity = builder.build();
		HttpEntity entity = core.getMyHttpClient().doPostFile(url, reqEntity);
		if (entity != null) {
			try {
				String result = EntityUtils.toString(entity, Consts.UTF_8);
				return JSON.parseObject(result);
			} catch (Exception e) {
				logger.error("webWxUploadMedia 错误： ", e);
			}

		}
		return null;
	}

	/**
	 * 根据用户id发送图片消息
	 * 
	 * @author https://github.com/yaphone
	 * @date 2017年5月7日 下午10:34:24
	 * @param userName
	 * @param filePath
	 * @return
	 */
	public static boolean sendPicMsg(String userName, String filePath) {
		JSONObject responseObj = uploadMediaFile(filePath);
		if (responseObj != null) {
			String mediaId = responseObj.getString("MediaId");
			if (mediaId != null) {
				return sendPicMsgMedia(userName, mediaId);
			}
		}
		return false;
	}

	/**
	 * 发送图片消息，内部调用
	 * 
	 * @author https://github.com/yaphone
	 * @date 2017年5月7日 下午10:38:55
	 * @return
	 */
	private static boolean sendPicMsgMedia(String userName, String mediaId) {
		if (!core.isAlive()) {
			logger.warn("微信已离线，消息发送已取消");
		}
		//
		Map<String, Object> loginInfo = core.getLoginInfo();
		String url = String.format("%s/webwxsendmsgimg?fun=async&f=json&pass_ticket=%s", loginInfo.get("url"), loginInfo.get("pass_ticket"));
		Map<String, Object> msgMap = new HashMap<String, Object>();
		msgMap.put("Type", 3);
		msgMap.put("MediaId", mediaId);
		msgMap.put("FromUserName", core.getUserName());
		msgMap.put("ToUserName", userName);
		String clientMsgId = String.valueOf(new Date().getTime()) + String.valueOf(new Random().nextLong()).substring(1, 5);
		msgMap.put("LocalID", clientMsgId);
		msgMap.put("ClientMsgId", clientMsgId);
		Map<String, Object> paramMap = core.newParamMap();
		paramMap.put("BaseRequest", core.newParamMap().get("BaseRequest"));
		paramMap.put("Msg", msgMap);
		String paramStr = JSON.toJSONString(paramMap);
		HttpEntity entity = core.getMyHttpClient().doPost(url, paramStr);
		if (entity != null) {
			try {
				String result = EntityUtils.toString(entity, Consts.UTF_8);
				return JSON.parseObject(result).getJSONObject("BaseResponse").getInteger("Ret") == 0;
			} catch (Exception e) {
				logger.error("webWxSendMsgImg 错误： ", e);
			}
		}
		return false;

	}

	/**
	 * 根据用户id发送文件
	 * 
	 * @author https://github.com/yaphone
	 * @date 2017年5月7日 下午11:57:36
	 * @param userName
	 * @param filePath
	 * @return
	 */
	public static boolean sendFileMsg(String userName, String filePath) {
		String title = new File(filePath).getName();
		Map<String, String> data = new HashMap<String, String>();
		data.put("appid", Config.API_WXAPPID);
		data.put("title", title);
		data.put("totallen", "");
		data.put("attachid", "");
		data.put("type", "6"); // APPMSGTYPE_ATTACH
		data.put("fileext", title.split("\\.")[1]); // 文件后缀
		JSONObject responseObj = uploadMediaFile(filePath);
		if (responseObj != null) {
			data.put("totallen", responseObj.getString("StartPos"));
			data.put("attachid", responseObj.getString("MediaId"));
		} else {
			logger.error("sednFileMsgByUserId 错误: ", data);
		}
		return sendAppMsg(userName, data);
	}

	/**
	 * 内部调用
	 * 
	 * @author https://github.com/yaphone
	 * @date 2017年5月10日 上午12:21:28
	 * @param userName
	 * @param data
	 * @return
	 */
	private static boolean sendAppMsg(String userName, Map<String, String> data) {
		if (!core.isAlive()) {
			logger.warn("微信已离线，消息发送已取消");
		}
		//
		Map<String, Object> loginInfo = core.getLoginInfo();
		String url = String.format("%s/webwxsendappmsg?fun=async&f=json&pass_ticket=%s", loginInfo.get("url"), loginInfo.get("pass_ticket"));
		String clientMsgId = String.valueOf(new Date().getTime()) + String.valueOf(new Random().nextLong()).substring(1, 5);
		String content = "<appmsg appid='wxeb7ec651dd0aefa9' sdkver=''><title>" + data.get("title") + "</title><des></des><action></action><type>6</type><content></content><url></url><lowurl></lowurl>" + "<appattach><totallen>"
				+ data.get("totallen") + "</totallen><attachid>" + data.get("attachid") + "</attachid><fileext>" + data.get("fileext") + "</fileext></appattach><extinfo></extinfo></appmsg>";
		Map<String, Object> msgMap = new HashMap<String, Object>();
		msgMap.put("Type", data.get("type"));
		msgMap.put("Content", content);
		msgMap.put("FromUserName", core.getUserName());
		msgMap.put("ToUserName", userName);
		msgMap.put("LocalID", clientMsgId);
		msgMap.put("ClientMsgId", clientMsgId);
		/*
		 * Map<String, Object> paramMap = new HashMap<String, Object>();
		 * 
		 * @SuppressWarnings("unchecked") Map<String, Map<String, String>> baseRequestMap = (Map<String, Map<String, String>>) loginInfo .get("baseRequest"); paramMap.put("BaseRequest",
		 * baseRequestMap.get("BaseRequest"));
		 */

		Map<String, Object> paramMap = core.newParamMap();
		paramMap.put("Msg", msgMap);
		paramMap.put("Scene", 0);
		String paramStr = JSON.toJSONString(paramMap);
		HttpEntity entity = core.getMyHttpClient().doPost(url, paramStr);
		if (entity != null) {
			try {
				String result = EntityUtils.toString(entity, Consts.UTF_8);
				return JSON.parseObject(result).getJSONObject("BaseResponse").getInteger("Ret") == 0;
			} catch (Exception e) {
				logger.error("错误: ", e);
			}
		}
		return false;
	}

	/**
	 * 被动添加好友
	 * 
	 * @date 2017年6月29日 下午10:08:43
	 * @param refMsg
	 * @param accept
	 *            true 接受 false 拒绝
	 */
	public static void addFriend(BaseMsg refMsg, boolean accept) {
		if (!core.isAlive()) {
			logger.warn("微信已离线，消息发送已取消");
		}
		//
		if (!accept) { // 不添加
			return;
		}
		//
		int status = VerifyFriendEnum.ACCEPT.getCode(); // 接受好友请求
		RecommendInfo recommendInfo = refMsg.getRecommendInfo();
		String userName = recommendInfo.getUserName();
		String ticket = recommendInfo.getTicket();
		// 更新好友列表
		// TODO 此处需要更新好友列表
		// core.getContactList().add(msg.getJSONObject("RecommendInfo"));
		Map<String, Object> loginInfo = core.getLoginInfo();
		String url = String.format(URLEnum.WEB_WX_VERIFYUSER.getUrl(), loginInfo.get("url"), String.valueOf(System.currentTimeMillis() / 3158L), loginInfo.get("pass_ticket"));

		List<Map<String, Object>> verifyUserList = new ArrayList<Map<String, Object>>();
		Map<String, Object> verifyUser = new HashMap<String, Object>();
		verifyUser.put("Value", userName);
		verifyUser.put("VerifyUserTicket", ticket);
		verifyUserList.add(verifyUser);

		List<Integer> sceneList = new ArrayList<Integer>();
		sceneList.add(33);

		JSONObject body = new JSONObject();
		body.put("BaseRequest", core.newParamMap().get("BaseRequest"));
		body.put("Opcode", status);
		body.put("VerifyUserListSize", 1);
		body.put("VerifyUserList", verifyUserList);
		body.put("VerifyContent", "");
		body.put("SceneListCount", 1);
		body.put("SceneList", sceneList);
		body.put("skey", loginInfo.get(StorageLoginInfoEnum.skey.getKey()));

		String result = null;
		try {
			String paramStr = JSON.toJSONString(body);
			HttpEntity entity = core.getMyHttpClient().doPost(url, paramStr);
			result = EntityUtils.toString(entity, Consts.UTF_8);
		} catch (Exception e) {
			logger.error("webWxSendMsg", e);
		}

		if (StringUtils.isBlank(result)) {
			logger.error("被动添加好友失败");
		}

		logger.info(result);

	}

	/**
	 * 
	 * 根据用户昵称设置备注名称
	 * 
	 * @date 2017年5月27日 上午12:21:40
	 * @param userName
	 * @param remName
	 */
	public static boolean setUserRemarkName(String userName, String remarkName) {
		if (!core.isAlive()) {
			logger.warn("微信已离线，消息发送已取消");
		}
		//
		Map<String, Object> loginInfo = core.getLoginInfo();
		String url = String.format(URLEnum.WEB_WX_REMARKNAME.getUrl(), loginInfo.get("url"), loginInfo.get(StorageLoginInfoEnum.pass_ticket.getKey()));
		Map<String, Object> msgMap = new HashMap<String, Object>();
		Map<String, Object> msgMap_BaseRequest = new HashMap<String, Object>();
		msgMap.put("CmdId", 2);
		msgMap.put("UserName", userName);
		msgMap.put("RemarkName", remarkName);
		msgMap_BaseRequest.put("Uin", loginInfo.get(StorageLoginInfoEnum.wxuin.getKey()));
		msgMap_BaseRequest.put("Sid", loginInfo.get(StorageLoginInfoEnum.wxsid.getKey()));
		msgMap_BaseRequest.put("Skey", loginInfo.get(StorageLoginInfoEnum.skey.getKey()));
		msgMap_BaseRequest.put("DeviceID", loginInfo.get(StorageLoginInfoEnum.deviceid.getKey()));
		msgMap.put("BaseRequest", msgMap_BaseRequest);
		try {
			String paramStr = JSON.toJSONString(msgMap);
			HttpEntity entity = core.getMyHttpClient().doPost(url, paramStr);
			// String result = EntityUtils.toString(entity, Consts.UTF_8);
			logger.info("修改了" + userName + "的备注名：" + remarkName);
			return true;
		} catch (Exception e) {
			logger.error(e.getMessage());
			return false;
		}
	}

}
