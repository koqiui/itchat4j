package cn.open.itchat4j.enums;

/**
 * 返回结构枚举类
 * <p>
 * Created by xiaoxiaomo on 2017/5/6.
 */
public enum ResultEnum {

	SUCCESS("200", "成功"), WAIT_CONFIRM("201", "请在手机上点击确认"), WAIT_SCAN("400", "请扫描二维码");

	private String code;
	private String message;

	ResultEnum(String code, String message) {
		this.code = code;
		this.message = message;
	}

	public String getCode() {
		return code;
	}

	public String getMessage() {
		return message;
	}

}
