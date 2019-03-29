package cn.open.itchat4j.enums;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 * 消息类型
 * 
 * @author https://github.com/yaphone
 * @date 创建时间：2017年4月23日 下午12:15:00
 * @version 1.0
 *
 */
public enum MsgTypeValueEnum {
	// public static final int MSGTYPE_TEXT = 1; // 文本消息类型
	// public static final int MSGTYPE_IMAGE = 3; // 图片消息
	// public static final int MSGTYPE_VOICE = 34; // 语音消息
	// public static final int MSGTYPE_VIDEO = 43; // 小视频消息
	// public static final int MSGTYPE_MICROVIDEO = 62; // 短视频消息
	// public static final int MSGTYPE_EMOTICON = 47; // 表情消息
	// public static final int MSGTYPE_APP = 49;
	// public static final int MSGTYPE_VOIPMSG = 50;
	// public static final int MSGTYPE_VOIPNOTIFY = 52;
	// public static final int MSGTYPE_VOIPINVITE = 53;
	// public static final int MSGTYPE_LOCATION = 48;
	// public static final int MSGTYPE_STATUSNOTIFY = 51;
	// public static final int MSGTYPE_SYSNOTICE = 9999;
	// public static final int MSGTYPE_POSSIBLEFRIEND_MSG = 40;
	// public static final int MSGTYPE_VERIFYMSG = 37;
	// public static final int MSGTYPE_SHARECARD = 42;
	// public static final int MSGTYPE_SYS = 10000;
	// public static final int MSGTYPE_RECALLED = 10002;
	MSGTYPE_TEXT(1, "文本消息"), //
	MSGTYPE_IMAGE(3, "图片消息"), //
	MSGTYPE_VOICE(34, "语音消息"), //
	MSGTYPE_VERIFYMSG(37, "好友请求"), //
	MSGTYPE_POSSIBLEFRIEND_MSG(40, ""), //
	MSGTYPE_SHARECARD(42, ""), //
	MSGTYPE_VIDEO(43, "视频消息"), //
	MSGTYPE_EMOTICON(47, "表情消息"), //
	MSGTYPE_LOCATION(48, "位置消息"), //
	MSGTYPE_MEDIA(49, "分享链接"), // 媒体??
	MSGTYPE_VOIPMSG(50, "VOIPMSG"), //
	MSGTYPE_STATUSNOTIFY(51, "状态通知"), //
	MSGTYPE_VOIPNOTIFY(52, "VOIPNOTIFY"), //
	MSGTYPE_VOIPINVITE(53, "VOIPINVITE"), //
	MSGTYPE_MICROVIDEO(62, "短视频消息"), //
	MSGTYPE_SYSNOTICE(9999, "SYSNOTICE"), //
	MSGTYPE_SYS(10000, "系统消息"), //
	MSGTYPE_RECALLED(10002, "撤回消息");

	private int value;
	private String name;

	MsgTypeValueEnum(int value, String name) {
		this.value = value;
		this.name = name;
	}

	public int getValue() {
		return value;
	}

	public String getName() {
		return name;
	}

	private static final Map<Integer, MsgTypeValueEnum> lookup = new HashMap<>();
	static {
		for (MsgTypeValueEnum elem : EnumSet.allOf(MsgTypeValueEnum.class)) {
			lookup.put(elem.getValue(), elem);
		}
	}

	public static MsgTypeValueEnum fromValue(Integer value) {
		return value == null ? null : lookup.get(value);
	}

}
