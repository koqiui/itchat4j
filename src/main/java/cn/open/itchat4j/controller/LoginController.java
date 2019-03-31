package cn.open.itchat4j.controller;

import java.io.StringWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSONObject;

import cn.open.itchat4j.WechatHelper;
import cn.open.itchat4j.core.Core;
import cn.open.itchat4j.core.CoreDataStore;
import cn.open.itchat4j.core.FileDataStore;
import cn.open.itchat4j.thread.CheckLoginStatusThread;
import cn.open.itchat4j.utils.SleepUtils;
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
	private static Core core = Core.getInstance();

	public LoginController() {
		// wechatHelper.initCore();
		String dataFilePath = SysUtils.selectByOs("E:/MiscData/swb/itchat/data.json", "/swb-base/data/itchat/data.json");
		FileDataStore dataStore = new FileDataStore(dataFilePath);
		wechatHelper.initCore(dataStore);
	}

	public void login(String qrPath) {
		if (core.isAlive()) {
			logger.info("已经登陆了");
			return;
		}
		logger.info("4. 试着登陆...");
		Boolean result = wechatHelper.tryToLogin();
		if (result == null) {
			logger.warn("正在试着登陆");
			return;
		} else if (Boolean.TRUE.equals(result)) {
			logger.info(("陆成功"));
		} else {
			for (int count = 0; count < 10; count++) {
				logger.info("1. 获取UUID");
				String uuid = null;
				while ((uuid = wechatHelper.getUuid(true)) == null) {
					logger.warn("1.1. 获取微信UUID失败，两秒后重新获取");
					SleepUtils.sleep(2000);
				}
				logger.info("2. 获取登陆二维码图片");
				if (wechatHelper.getAndOpenQrImage(uuid, qrPath)) {
					break;
				} else if (count == 10) {
					logger.error("2.1. 获取登陆二维码图片失败");
					return;
				}
			}
			//
			logger.info("3. 请扫描二维码图片，并在手机上确认");
			//
			this.login(qrPath);
		}
		//
		logger.info("5. 微信初始化");
		wechatHelper.initBasicInfo();

		logger.info("6. 开启微信状态通知");
		wechatHelper.initStatusNotify();

		// logger.info("7. 清除。。。。");
		// CommonTools.clearScreen();
		logger.info(String.format("欢迎回来， %s", core.getNickName()));

		logger.info("8. 开始接收消息");
		wechatHelper.startReceiving();

		logger.info("9. 获取联系人信息");
		wechatHelper.fetchContacts();

		logger.info("10. 获取群好友及群好友列表");
		wechatHelper.fetchGroups();

		logger.info("12.开启微信状态检测线程");

		// CoreDataStore dataStore = core.getDataStore();
		// StringWriter sw = new StringWriter();
		// JSONObject.writeJSONString(sw, dataStore.getAll());
		// System.out.println(sw.toString());

		new Thread(new CheckLoginStatusThread()).start();
	}
}