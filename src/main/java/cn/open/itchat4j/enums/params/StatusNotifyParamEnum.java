package cn.open.itchat4j.enums.params;

/**
 * 状态通知
 * <p>
 * Created by xiaoxiaomo on 2017/5/7.
 */
public enum StatusNotifyParamEnum {

	CODE("Code", "3"), FROM_USERNAME("FromUserName", ""), TO_USERNAME("ToUserName", ""), CLIENT_MSG_ID("ClientMsgId", ""); // 时间戳

	private String param;
	private String value;

	StatusNotifyParamEnum(String param, String value) {
		this.param = param;
		this.value = value;
	}

	public String param() {
		return param;
	}

	public String value() {
		return value;
	}
}
