package cn.open.itchat4j.enums.params;

/**
 * UUID
 * <p>
 * Created by xiaoxiaomo on 2017/5/7.
 */
public enum UUIDParamEnum {

	APP_ID_OLD("appid", "wx782c26e4c19acffb"), //
	APP_ID_NEW("appid", "wxeb7ec651dd0aefa9"), //
	FUN("fun", "new"), //
	LANG("lang", "zh_CN"), //
	_("_", "时间戳");

	private String param;
	private String value;

	UUIDParamEnum(String param, String value) {
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
