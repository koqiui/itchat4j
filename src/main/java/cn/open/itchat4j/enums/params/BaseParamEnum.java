package cn.open.itchat4j.enums.params;

/**
 *
 * 基本请求参数 1. webWxInit 初始化 2. wxStatusNotify 微信状态通知
 *
 * <p>
 * Created by xiaoxiaomo on 2017/5/7.
 */
public enum BaseParamEnum {

	Uin("Uin", "wxuin"), //
	Sid("Sid", "wxsid"), //
	Skey("Skey", "skey"), //
	DeviceID("DeviceID", "pass_ticket");

	private String param;
	private String value;

	BaseParamEnum(String param, String value) {
		this.param = param;
		this.value = value;
	}

	public String param() {
		return param;
	}

	public Object value() {
		return value;
	}

}
