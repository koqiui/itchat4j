package cn.open.itchat4j.enums;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

public enum RetCodeEnum {

	SUCCESS("0", "正常"), //
	TICKET_ERROR("-14", "ticket错误或无效"), //
	PARAM_ERROR("1", "参数错误"), //
	DEVICE_FAIL("3", "设备验证失败"), //
	NOT_LOGIN_WARN("1100", "未登录或已退出"), //
	LOGIN_OTHERWHERE("1101", "可能已在别处登陆"), //
	INVALID_COOKIE("1102", "给定的cookie值无效"), // 移动端退出
	LOGIN_ENV_ERROR("1203", "当前登录环境异常，为了安全起见请不要在web端进行登录"), //
	TOO_OFTEN("1205", "操作过于频繁"), //
	UNKOWN("9999", "未知结果信息");

	private String code;
	private String message;

	RetCodeEnum(String code, String message) {
		this.code = code;
		this.message = message;
	}

	public String getCode() {
		return code;
	}

	public String getMessage() {
		return message;
	}

	private static final Map<String, RetCodeEnum> lookup = new HashMap<String, RetCodeEnum>();
	static {
		for (RetCodeEnum elem : EnumSet.allOf(RetCodeEnum.class)) {
			lookup.put(elem.getCode(), elem);
		}
	}

	public static RetCodeEnum fromCode(String code) {
		return code == null ? null : lookup.get(code);
	}

}
