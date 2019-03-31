package cn.open.itchat4j.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.open.itchat4j.WechatHelper;
import cn.open.itchat4j.core.FileDataStore;
import cn.open.itchat4j.utils.SysUtils;

/**
 * 登陆控制器
 * 
 * @author https://github.com/yaphone
 * @date 创建时间：2017年5月13日 下午12:56:07
 * @version 1.0
 *
 */
public class LoginController {
	private static Logger logger = LoggerFactory.getLogger(LoginController.class);
	private WechatHelper wechatHelper = WechatHelper.getInstance();

	public LoginController() {
		// wechatHelper.initCore();
		String dataFilePath = SysUtils.selectByOs("E:/MiscData/swb/itchat/data.json", "/swb-base/data/itchat/data.json");
		FileDataStore dataStore = new FileDataStore(dataFilePath);
		wechatHelper.initCore(dataStore);
	}

	public void login(String qrPath) {
		wechatHelper.doLogin(qrPath);
	}
}