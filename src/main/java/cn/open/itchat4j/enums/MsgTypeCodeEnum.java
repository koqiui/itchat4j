package cn.open.itchat4j.enums;

/**
 * 消息类型枚举类
 * 
 * @author https://github.com/yaphone
 * @date 创建时间：2017年5月13日 下午11:53:00
 * @version 1.0
 *
 */
public enum MsgTypeCodeEnum {
	TEXT("Text", "文本消息"), //
	PIC("Pic", "图片消息"), //
	VOICE("Voice", "语音消息"), //
	VIEDO("Viedo", "小视频消息"), //
	NAMECARD("NameCard", "名片消息"), //
	SYS("Sys", "系统消息"), //
	VERIFYMSG("VerifyMsg", "添加好友"), //
	MEDIA("app", "文件消息");

	private String code;
	private String name;

	MsgTypeCodeEnum(String code, String name) {
		this.code = code;
		this.name = name;
	}

	public String getCode() {
		return code;
	}

	public String getName() {
		return name;
	}

}
