package cn.open.itchat4j.enums.params;

/**
 * 登陆
 * <p>
 * Created by xiaoxiaomo on 2017/5/7.
 */
public enum LoginParamEnum {

	LOGIN_ICON("loginicon", "true"), //
	UUID("uuid", ""), //
	TIP("tip", "0"), //
	R("r", ""), //
	_("_", "");

	private String param;
	private String value;

	LoginParamEnum(String param, String value) {
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
