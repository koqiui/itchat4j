package cn.open.itchat4j.core;

import java.io.Serializable;
import java.util.Date;
import java.util.Map;

import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.cookie.BasicClientCookie;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

/**
 * 微信用户相关信息存储接口
 * 
 * @author koqiui
 * @date 2019年3月31日 下午8:38:43
 *
 */
public interface CoreDataStore extends Serializable {

	void clear();

	void init(Map<String, Object> initValues);

	boolean load();

	<T> T get(String key);

	<T> T get(String key, T ifNullValue);

	void set(String key, Object value);

	void del(String key);

	boolean save();

	Map<String, Object> getAll();

	//
	public static MsgUser toMsgUser(JSONObject userJson) {
		if (userJson != null) {
			return JSON.toJavaObject(userJson, MsgUser.class);
		}
		return null;
	}

	public static BasicCookieStore toCookieStore(JSONObject cookieStoreJson) {
		if (cookieStoreJson != null) {
			BasicCookieStore cookieStore = new BasicCookieStore();
			JSONArray tempCookies = cookieStoreJson.getJSONArray("cookies");
			for (int i = 0; i < tempCookies.size(); i++) {
				JSONObject tempCookie = tempCookies.getJSONObject(i);
				BasicClientCookie cookie = new BasicClientCookie(tempCookie.getString("name"), tempCookie.getString("value"));
				cookie.setDomain(tempCookie.getString("domain"));
				cookie.setSecure(tempCookie.getBooleanValue("secure"));
				cookie.setPath(tempCookie.getString("path"));
				cookie.setExpiryDate(new Date(tempCookie.getLongValue("expiryDate")));
				//
				cookieStore.addCookie(cookie);
			}
			//
			return cookieStore;
		}
		return null;
	}
}
