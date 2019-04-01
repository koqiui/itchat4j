package cn.open.itchat4j.core;

import java.io.Serializable;

public class MsgUser implements Serializable {
	private static final long serialVersionUID = 1L;
	//
	public Integer userType;
	public String userName;
	public String nickName;
	public String remarkName;
	public String headImgUrl;
	public String dispName;
	public String signature;
}
