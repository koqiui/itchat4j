package cn.open.itchat4j.tools;

import java.io.StringReader;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.vdurmont.emoji.EmojiParser;

import cn.open.itchat4j.utils.Config;

/**
 * 常用工具类
 * 
 * @author https://github.com/yaphone
 * @date 创建时间：2017年4月8日 下午10:59:55
 * @version 1.0
 *
 */
public class CommonTools {

	public static boolean formatEmojiAsAlias = false;
	// 不可解析的emoji替代字符
	public static String unparsibleEmojiReplacement = null;

	public static boolean printQr(String qrPath) {
		Runtime runtime = Runtime.getRuntime();
		switch (Config.getOsNameEnum()) {
			case WINDOWS:
				try {
					runtime.exec("cmd /c start " + qrPath);
				} catch (Exception e) {
					e.printStackTrace();
				}
				break;
			case MAC:
				try {
					runtime.exec("open " + qrPath);
				} catch (Exception e) {
					e.printStackTrace();
				}
				break;

			default:
				break;
		}
		return true;
	}

	public static boolean clearScreen() {
		Runtime runtime = Runtime.getRuntime();
		switch (Config.getOsNameEnum()) {
			case WINDOWS:
				try {
					runtime.exec("cmd /c " + "cls");
				} catch (Exception e) {
					e.printStackTrace();
				}
				break;
			case MAC:
				try {
					runtime.exec("clear ");
				} catch (Exception e) {
					e.printStackTrace();
				}
				break;
			default:
				break;
		}
		return true;
	}

	/**
	 * 正则表达式处理工具
	 * 
	 * @author https://github.com/yaphone
	 * @date 2017年4月9日 上午12:27:10
	 * @return
	 */
	public static Matcher getMatcher(String regEx, String text) {
		Pattern pattern = Pattern.compile(regEx);
		Matcher matcher = pattern.matcher(text);
		return matcher;
	}

	/**
	 * xml解析器
	 * 
	 * @author https://github.com/yaphone
	 * @date 2017年4月9日 下午6:24:25
	 * @param text
	 * @return
	 */
	public static Document xmlParser(String text) {
		Document doc = null;
		StringReader sr = new StringReader(text);
		InputSource is = new InputSource(sr);
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		try {
			DocumentBuilder builder = factory.newDocumentBuilder();
			doc = builder.parse(is);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return doc;
	}

	public static String getSynckey(JSONObject obj) {
		JSONArray obj2 = obj.getJSONArray("List");
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < obj2.size(); i++) {
			JSONObject obj3 = (JSONObject) JSON.toJSON(obj2.get(i));
			sb.append(obj3.get("Val") + "|");
		}
		return sb.substring(0, sb.length() - 1); // 656159784|656159911|656159873|1491905341

	}

	public static JSONObject searchDictList(List<JSONObject> list, String key, String value) {
		JSONObject ret = null;
		for (JSONObject i : list) {
			if (i.getString(key).equals(value)) {
				ret = i;
				break;
			}
		}
		return ret;
	}

	//
	public static String parseEmoji(String content) {
		Matcher matcher = getMatcher("<span class=\"emoji emoji(.+?)\"></span>", content);
		StringBuilder sb = new StringBuilder();
		int lastStart = 0;
		while (matcher.find()) {
			String str = matcher.group(1);
			if (str.length() == 6 || str.length() == 10) {
				str = unparsibleEmojiReplacement == null ? '#' + str + '#' : unparsibleEmojiReplacement;
			} else {
				str = "&#x" + str + ";";
			}
			String tmp = content.substring(lastStart, matcher.start());
			sb.append(tmp + str);
			lastStart = matcher.end();
		}
		if (lastStart < content.length()) {
			sb.append(content.substring(lastStart));
		}
		if (sb.length() != 0) {
			return EmojiParser.parseToUnicode(sb.toString());
		} else {
			return content;
		}
	}

	public static String parseEmojiAsAlias(String content) {
		Matcher matcher = getMatcher("<span class=\"emoji emoji(.+?)\"></span>", content);
		StringBuilder sb = new StringBuilder();

		int lastStart = 0;
		while (matcher.find()) {
			String str = matcher.group(1);
			if (str.length() == 6 || str.length() == 10) {
				str = unparsibleEmojiReplacement == null ? '#' + str + '#' : unparsibleEmojiReplacement;
			} else {
				str = "&#x" + str + ";";
				String tmp = content.substring(lastStart, matcher.start());
				sb.append(tmp + str);
				lastStart = matcher.end();
			}
		}
		if (lastStart < content.length()) {
			sb.append(content.substring(lastStart));
		}
		if (sb.length() != 0) {
			return EmojiParser.parseToAliases(EmojiParser.parseToUnicode(sb.toString()));
		} else {
			return content;
		}
	}

	/**
	 * 处理emoji表情成alias
	 * 
	 * @param d
	 * @param k
	 */
	public static void filterMsgEmojiAsAlias(JSONObject d, String k) {
		String content = d.getString(k);
		d.put(k, parseEmojiAsAlias(content));
	}

	/**
	 * 处理emoji表情
	 * 
	 * @author https://github.com/yaphone
	 * @date 2017年4月23日 下午2:39:04
	 * @param d
	 * @param k
	 */
	public static void filterMsgEmoji(JSONObject d, String k) {
		String content = d.getString(k);
		d.put(k, parseEmoji(content));
	}

	/**
	 * 消息格式化
	 * 
	 * @author https://github.com/yaphone
	 * @date 2017年4月23日 下午4:19:08
	 * @param d
	 * @param k
	 */
	public static void filterMsgEmojiEx(JSONObject d, String k, boolean formatEmoji) {
		String content = d.getString(k);
		content = content.replace("&amp;", "&");
		content = content.replace("<br/>", "\n");
		d.put(k, content);
		if (formatEmoji) {
			if (formatEmojiAsAlias) {
				filterMsgEmojiAsAlias(d, k);
			} else {
				filterMsgEmoji(d, k);
			}
		}
		// TODO 与emoji表情有部分兼容问题，目前暂未处理解码处理 d.put(k,
		// StringEscapeUtils.unescapeHtml4(d.getString(k)));

	}

	public static void filterMsgEmojiEx(JSONObject d, String k) {
		filterMsgEmojiEx(d, k, true);
	}

}
