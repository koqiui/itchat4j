# itchat4j -- 用Java扩展个人微信号的能力

 

### 项目地址：[itchat4j](https://github.com/koqiui/itchat4j)，欢迎star、fork、 pull requests、 issue。

## 示例项目程序[点击此处下载](https://github.com/koqiui/itchat4jdemo)。

### 来源

[itchat](https://github.com/littlecodersh/ItChat)是一个非常优秀的开源微信个人号接口，使用Python语言开发，提供了简单易用的API，可以很方便地对个人微信号进行扩展，实现自动回复，微信挂机机器人等，一直在关注这个项目，基于itchat开发过[一个小项目](https://github.com/koqiui/RasWxNeteaseMusic)，用来控制我的树莓派来播放音乐，效果还不错。


## 项目介绍

> itchat是一个开源的微信个人号接口，使用Python调用微信从未如此简单。使用短短的几十行代码，你就可以完成一个能够处理所有信息的微信机器人。当然，itchat的使用远不止一个机器人，更多的功能等着你来发现，如今微信已经成为了个人社交的很大一部分，希望这个项目能够帮助你扩展你的个人的微信号、方便自己的生活。(引自itchat项目)



## itchat4j 修正加强版 适用于 本地 + web多节点 应用

> 1、公开包名、简化包结构、命名重构（主要类：WechatHelper 和 MsgHelper）

> 2、支持自定义数据存储(提供了CoreDataStore的实现： 内存 MemDataStore 和 文件 存储FileDataStore)和加载

> 3、支持事件通知（CoreStateListener：onUserOnline, onUserReady, onUserOffline, onDataChanged, onUuidRefreshed, onWaitForScan, onLoginFail）

> 4、支持hot reload（自动）、刷新扫码重新登陆、数据定期检查和保存、减少接口方法、（结合中央存储）支持web端 多节点应用

> 5、支持获取（自己、朋友、群组的）基本信息（MsgUserInfo，因为每次登陆userName都不同） 和 头像图片（支持按用户类型+用户别名的md5文件名进行图像磁盘缓存）

> 6、基于5，支持按（用户类型和）nickName 发送消息（增加离线拒发）

> 7、提供win和mac两种UserAgent，支持因状态错乱而无法获取uuid时自动更新deviceId并切换UserAgent	
	
> 主要参考：
	itchat4jdemo下的 itchat4jtest.demo.demo1.DemoClient
	itchat4j 下的 Wechat（客户端演示用）, WechatHelper	

		

```java

public class DemoClient {
	public static void main(String[] args) {
		MsgHandler msgHandler = new DemoMsgHandler();

		Wechat wechat = new Wechat(msgHandler);

		// 如果指定图片保存目录则从本地打开（仅用于测试，web应用下不要设置，从getQrImageUrl(false)即可获取 二维码图片url ）
		String qrImageFileDir = SysUtils.selectByOs("E:/MiscData/swb/itchat", "/swb-base/data/itchat");

		// 登陆用户相关数据存储文件（如果不提供则使用内存模型，不包括消息，但很容易支持）
		String dataStoreFilePath = SysUtils.selectByOs("E:/MiscData/swb/itchat/data.json", "/swb-base/data/itchat/data.json");

		// 缓存用户头像的目录
		String headImgCacheDir = SysUtils.selectByOs("E:/MiscData/swb/itchat/head", "/swb-base/data/itchat/head");
		// 无头像的默认替代头像文件名（绝对路径 或 相对于头像缓存目录，用于 离线获取不到头像 或 未设头像或的场景）
		String headImgFaultFileName = "default-head-img.jpg";

		wechat.start(qrImageFileDir, dataStoreFilePath, headImgCacheDir, headImgFaultFileName);
	}

}
```


```java
public class Wechat {
	private static final Logger logger = LoggerFactory.getLogger(Wechat.class);
	//
	private WechatHelper wechatHelper = WechatHelper.getInstance();
	private MsgHandler msgHandler;

	public Wechat(MsgHandler msgHandler) {
		this.msgHandler = msgHandler;
	}

	public void start(String qrImageFileDir) {
		this.start(qrImageFileDir, null, null, null);
	}

	public void start(String qrImageFileDir, String dataStoreFilePath) {
		this.start(dataStoreFilePath, qrImageFileDir, null, null);
	}

	public void start(String qrImageFileDir, String dataStoreFilePath, String headImgCacheDir) {
		this.start(dataStoreFilePath, qrImageFileDir, headImgCacheDir, null);
	}

	public void start(String qrImageFileDir, String dataStoreFilePath, String headImgCacheDir, String headImgFaultFileName) {
		// 消息处理线程
		Thread msgThread = MsgCenter.handleMsgs(msgHandler, false);
		// 状态监听示例
		CoreStateListener stateListener = new CoreStateListener() {

			@Override
			public void onUserOnline(String nodeName) {
				logger.info("微信在本节点  " + nodeName + " 上线");
				// TODO 发送广播消息(nodeName, online, true)
			}

			@Override
			public void onUserReady(String nodeName) {
				logger.info("微信在本节点  " + nodeName + " 就绪");
				// TODO 发送广播消息(nodeName, ready, true)
			}

			@Override
			public void onUserOffline(String nodeName) {
				logger.info("微信从本节点 " + nodeName + " 下线");
				// TODO 发送广播消息(nodeName, offline, true)

				if (!wechatHelper.isWaitingForLoginScan()) {// 扫码重新登陆的离线不停止
					// 下面仅仅是演示用（一般在应用停止时才会执行）
					msgThread.interrupt();
					//
					wechatHelper.shutdown();
				}
			}

			@Override
			public void onDataChanged(String nodeName, long dataVersion) {
				Date dataDate = new Date(dataVersion);
				SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
				logger.info("数据在本节点 " + nodeName + " 的 " + formatter.format(dataDate) + " 发生更改");
				// TODO 发送广播消息(nodeName, data-changed)
			}

			@Override
			public void onUuidRefreshed(String nodeName, String uuid) {
				logger.info("微信在本节点 " + nodeName + " 刷新了uuid : " + uuid);
				logger.info(wechatHelper.getQrImageUrlByUuid(uuid));
				// TODO 发送广播消息(nodeName, uuid-changed, uuid)
			}

			@Override
			public void onWaitForScan(String nodeName, boolean waiting) {
				logger.info("微信在本节点 " + nodeName + " 等着扫码登陆：");
				// TODO 发送广播消息(nodeName, waiting, waiting?)

				// 下面仅仅是演示用
				if (waiting) {
					logger.info("正在等着扫码，或打开如下url扫码登陆：");
					logger.info(wechatHelper.getQrImageUrl(false));
				}
			}

			@Override
			public void onLoginFail(String nodeName, String message) {
				logger.info("微信在本节点 " + nodeName + " 等着扫码登陆：");
				// TODO 发送广播消息(nodeName, loginFail, message)
				// 比如发送邮件通知用户本人或开发者处理

			}

		};
		// wechatHelper.setUseNewVersion(true);
		// 1
		wechatHelper.setNodeName("demoNode");
		// 设置头像缓存目录
		wechatHelper.setHeadImgCacheDir(headImgCacheDir);
		// 设置无头像的默认替代头像文件名（绝对路径 或 相对于头像缓存目录，用于 离线获取不到头像 或 未设头像或的场景）
		wechatHelper.setHeadImgFaultFileName(headImgFaultFileName);
		// 2
		if (dataStoreFilePath == null) {
			wechatHelper.initCore(stateListener); // 默认使用MemDataStore
		} else {
			FileDataStore dataStore = new FileDataStore(dataStoreFilePath);
			wechatHelper.initCore(dataStore, stateListener);
		}
		// 3
		wechatHelper.startup();
		// 4
		wechatHelper.doLogin(qrImageFileDir);

		//
		logger.info("+++++++++++++++++++开始消息处理+++++++++++++++++++++");
		msgThread.start();

		// 等待登陆上线就绪
		while (!wechatHelper.isReady()) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				logger.warn("可能已强制结束");
				System.exit(-1);
			}
		}

		logger.info(" -------- 获取 信息及头像 -------");
		logger.info(JSON.toJSONString(wechatHelper.getNickSelf(), true));
		wechatHelper.getNickSelfHeadImgBytes();

		logger.info(" -------- 获取 好友 信息及头像 -------");
		List<JSONObject> wxUsers = wechatHelper.getFriendList();
		for (int i = 0; i < wxUsers.size(); i++) {
			JSONObject wxUser = wxUsers.get(i);
			String nickName = wxUser.getString("NickName");
			logger.info(JSON.toJSONString(wechatHelper.getNickFriendUser(nickName), true));
			wechatHelper.getNickFriendHeadImgBytes(nickName);
		}

		logger.info(" -------- 获取 群组 信息及头像 -------");
		wxUsers = wechatHelper.getGroupList();
		for (int i = 0; i < wxUsers.size(); i++) {
			JSONObject wxUser = wxUsers.get(i);
			String nickName = wxUser.getString("NickName");
			logger.info(JSON.toJSONString(wechatHelper.getNickGroupUser(nickName), true));
			wechatHelper.getNickGroupHeadImgBytes(nickName);
		}

		// 模拟登陆态刷新二维码（从而除服重新登陆）
		// wechatHelper.getQrImageUrl(true);

		// // 模拟消息发送
		// String nickName = "😀ོ ꧁灬尼莫灬꧂";
		// MsgUser user = wechatHelper.getNickNameUser(MsgUserType.Friend, nickName);
		// logger.debug(JSON.toJSONString(user));
		// MsgHelper.sendTextMsgByNickName(MsgUserType.Friend, nickName, "这是从我的微信模拟客户端发出的消息");

		// 等待30秒
		try {
			Thread.sleep(20000);
		} catch (InterruptedException e) {
			logger.warn("可能已强制结束");
			System.exit(-1);
		}
		// 演示不再处理接收的消息
		wechatHelper.setHandleRecvMsgs(false);
	}

}
```

## 类似项目

[itchat](https://github.com/littlecodersh/ItChat) ：优秀的、基于Python的微信个人号API，同时也是本项目的灵感之源。

[WeixinBot](https://github.com/Urinx/WeixinBot): 网页版微信API，包含终端版微信及微信机器人

## 致谢：

itchat4j开源后，收到很多朋友的建议，对ithcat4j改进做出了很多帮助，在此表示感谢！

[@jasonTangxd](https://github.com/jasonTangxd?tab=overview&from=2017-05-15)，项目结构调整。

[@libre818](https://github.com/libre818)。

@QQ群好友（北极心 851668663）,增加修改好友备注名方法。

@QQ群好友（beyond_12345@126.com）

以及[每位PR的朋友](https://github.com/koqiui/itchat4j/graphs/contributors)！

## 问题和建议

本项目长期更新、维护，功能不断扩展与完善中，欢迎star。

项目使用过程中遇到问题，或者有好的建议，欢迎随时反馈。

任何问题或者建议都可以在Issue中提出来，也可以加入QQ群讨论：636365179

