package cn.open.itchat4j.enums;

/**
 * URL Created by xiaoxiaomo on 2017/5/6.
 */
public enum URLEnum {

	BASE_URL("https://login.weixin.qq.com", "基本的URL"), //
	UUID_URL(BASE_URL.url + "/jslogin", "登录UUID URL"), //
	QRCODE_URL(BASE_URL.url + "/qrcode/", "登陆二维码URL"), //
	LOGIN_URL(BASE_URL.url + "/cgi-bin/mmwebwx-bin/login", "登陆URL"), //
	INIT_URL("%s/webwxinit?r=%s&pass_ticket=%s", "初始化URL"), //
	SYNC_CHECK_URL("/synccheck", "检查心跳URL"), //
	STATUS_NOTIFY_URL(BASE_URL.url + "/webwxstatusnotify?lang=zh_CN&pass_ticket=%s", "微信状态通知"), //
	WEB_WX_SYNC_URL("%s/webwxsync?sid=%s&skey=%s&pass_ticket=%s", "web微信消息同步URL"), //
	WEB_WX_GET_CONTACT("%s/webwxgetcontact", "web微信获取联系人信息URL"), //
	WEB_WX_SEND_MSG("%s/webwxsendmsg", "发送消息URL"), //
	WEB_WX_UPLOAD_MEDIA("%s/webwxuploadmedia?f=json", "上传文件到服务器"), //
	WEB_WX_GET_MSG_IMG("%s/webwxgetmsgimg", "下载图片消息"), //
	WEB_WX_GET_VOICE("%s/webwxgetvoice", "下载音频消息"), //
	WEB_WX_GET_VIEDO("%s/webwxgetvideo", "下载视频消息"), //
	WEB_WX_GET_MEDIA("%s/webwxgetmedia", "下载文件"), //
	WEB_WX_PUSH_LOGIN("%s/webwxpushloginurl?uin=%s", "不扫码登陆"), //
	WEB_WX_BATCH_GET_CONTACT("%s/webwxbatchgetcontact?type=ex&r=%s&lang=zh_CN&pass_ticket=%s", "查询群信息"), //
	WEB_WX_REMARKNAME("%s/webwxoplog?lang=zh_CN&pass_ticket=%s", "修改好友备注"), //
	WEB_WX_VERIFYUSER("%s/webwxverifyuser?r=%s&lang=zh_CN&pass_ticket=%s", "被动添加好友"), //
	WEB_WX_LOGOUT("%s/webwxlogout", "退出微信"),//
	WEB_WX_URL("https://wx.qq.com", "微信基本url");

	private String url;
	private String desc;

	URLEnum(String url, String desc) {
		this.url = url;
		this.desc = desc;
	}

	public String getUrl() {
		return url;
	}

	public String getDesc() {
		return desc;
	}
}
