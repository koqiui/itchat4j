# itchat4j -- 用Java扩展个人微信号的能力

 

### 项目地址：[itchat4j](https://github.com/koqiui/itchat4j)，该项目长期维护更新，欢迎star、fork、 pull requests、 issue。

## 示例项目程序[点击此处下载](https://github.com/koqiui/itchat4jdemo)。

### 来源

[itchat](https://github.com/littlecodersh/ItChat)是一个非常优秀的开源微信个人号接口，使用Python语言开发，提供了简单易用的API，可以很方便地对个人微信号进行扩展，实现自动回复，微信挂机机器人等，一直在关注这个项目，基于itchat开发过[一个小项目](https://github.com/koqiui/RasWxNeteaseMusic)，用来控制我的树莓派来播放音乐，效果还不错。


## 项目介绍

> itchat是一个开源的微信个人号接口，使用Python调用微信从未如此简单。使用短短的几十行代码，你就可以完成一个能够处理所有信息的微信机器人。当然，itchat的使用远不止一个机器人，更多的功能等着你来发现，如今微信已经成为了个人社交的很大一部分，希望这个项目能够帮助你扩展你的个人的微信号、方便自己的生活。(引自itchat项目)



## itchat4j 改造版
		-------------------------------
		1、公开包名、简化包结构、命名重构（主要类：WechatHelper）
		2、支持自定义数据存储(提供了CoreDataStore的实现： 内存 MemDataStore 和 文件 存储FileDataStore)和加载
		3、支持hot reload（自动）、数据定期检查和保存、减少接口方法、支持web端应用
		4、支持按（用户类型和）nickName 获取 userName（因为每次登陆userName都不同）
		5、支持事件通知（CoreStateListener：onUserOnline, onUserOffline, onDataChanged, onUuidRefreshed）
		
		主要参考：
			itchat4jdemo下的 itchat4jtest.demo.demo1.DemoClient
			itchat4j 下的 Wechat（客户端演示用）, WechatHelper




	/**
	 * 
	 * @author https://github.com/koqiui
	 * @date 
	 *
	 */
	public class DemoClient {
		public static void main(String[] args) {
			MsgHandler msgHandler = new DemoMsgHandler();
	
			Wechat wechat = new Wechat(msgHandler);
	
			// 登陆用户相关数据存储文件（如果不提供则使用内存模型，不包括消息，但很容易支持）
			String dataStoreFilePath = SysUtils.selectByOs("E:/MiscData/swb/itchat/data.json", "/swb-base/data/itchat/data.json");
			// 保存登陆二维码图片的目录（如果不提供则可以在浏览器中打开对应的二维码扫描）
			String qrImageFileDir = SysUtils.selectByOs("E:/MiscData/swb/itchat", "/swb-base/data/itchat");
	
			wechat.start(dataStoreFilePath, qrImageFileDir);
		}
	
	}



	public class Wechat {
		private static final Logger logger = LoggerFactory.getLogger(Wechat.class);
		//
		private WechatHelper wechatHelper = WechatHelper.getInstance();
		private MsgHandler msgHandler;
	
		public Wechat(MsgHandler msgHandler) {
			this.msgHandler = msgHandler;
		}
	
		public void start(String dataStoreFilePath, String qrImageFileDir) {
			// 消息处理线程
			Thread msgThread = MsgCenter.handleMsgs(msgHandler, false);
			// 状态监听示例
			CoreStateListener stateListener = new CoreStateListener() {
	
				@Override
				public void onUserOnline(String nodeName) {
					logger.info("微信已在 " + nodeName + " 上线");
					// TODO 在中央缓存更新节点在线状态
				}
	
				@Override
				public void onUserOffline(String nodeName) {
					logger.info("微信已从 " + nodeName + " 下线");
					// TODO 在中央缓存更新节点在线状态
	
					// 下面仅仅是演示用（一般在应用停止时执行）
					msgThread.interrupt();
					//
					wechatHelper.shutdown();
				}
	
				@Override
				public void onDataChanged(long dataVersion) {
					Date dataDate = new Date(dataVersion);
					SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
					logger.info("数据在  " + formatter.format(dataDate) + " 更改");
					// TODO
				}
	
				@Override
				public void onUuidRefreshed() {
					logger.info("微信已刷新uuid已刷新，可打开如下url扫码登陆：");
					logger.info(wechatHelper.getQrImageUrl(false));
					// TODO 在中央缓存更新扫码url
				}
			};
			//
			if (dataStoreFilePath == null) {
				wechatHelper.initCore(); // 默认使用MemDataStore
			} else {
				FileDataStore dataStore = new FileDataStore(dataStoreFilePath);
				wechatHelper.initCore(dataStore, stateListener);
			}
			wechatHelper.setNodeName("demoNode");
			wechatHelper.startup();
			//
			wechatHelper.doLogin(qrImageFileDir);
			//
			logger.info("+++++++++++++++++++开始消息处理+++++++++++++++++++++");
			msgThread.start();
			//
			try {
				Thread.currentThread().sleep(10000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			MsgHelper.sendTextMsgByNickName(MsgUserType.Friend, "😀ོ ꧁灬尼莫灬꧂", "这是从我的微信模拟客户端发出的消息");
		}
	
	}
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

