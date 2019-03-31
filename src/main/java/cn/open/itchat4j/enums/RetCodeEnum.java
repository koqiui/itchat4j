package cn.open.itchat4j.enums;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

public enum RetCodeEnum {

	SUCCESS("0", "普通"), //
	TICKET_ERROR("-14", "ticket错误"), //
	PARAM_ERROR("1", "传入参数错误"), //
	NOT_LOGIN_WARN("1100", "未登录提示"), //
	LOGIN_OTHERWHERE("1101", "其它地方登陆"), //
	MOBILE_LOGIN_OUT("1102", "cookie值无效"), // 移动端退出
	LOGIN_ENV_ERROR("1203", "当前登录环境异常，为了安全起见请不要在web端进行登录"), //
	TOO_OFTEN("1205", "操作频繁"), //
	UNKOWN("9999", "未知");

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
