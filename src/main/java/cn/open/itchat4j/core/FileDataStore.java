package cn.open.itchat4j.core;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

public class FileDataStore implements CoreDataStore {
	private static final long serialVersionUID = 1L;
	private Logger logger = LoggerFactory.getLogger(this.getClass().getName());
	//
	private Map<String, Object> dataMap = new HashMap<String, Object>();

	private String dataFilePath;

	public FileDataStore(String dataFilePath) {
		this.dataFilePath = dataFilePath;
	}

	@Override
	public void clear() {
		dataMap.clear();
	}

	public BasicCookieStore toCookieStore(JSONObject cookieStoreJson) {
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

	// 设置默认值（防止报错）
	public void init(Map<String, Object> initValues) {
		if (initValues != null) {
			String key = null;
			Object val = null;
			for (Map.Entry<String, Object> initItem : initValues.entrySet()) {
				key = initItem.getKey();
				if (dataMap.get(key) == null) {
					val = initItem.getValue();
					dataMap.put(key, val);
					logger.info("默认值：" + key + " => " + val);
				}
			}
		}
	}

	@Override
	public boolean load() {
		try {
			File dataFile = new File(this.dataFilePath);
			if (dataFile.exists()) {
				BufferedReader dataReader = new BufferedReader(new InputStreamReader(new FileInputStream(dataFile), Charset.forName("UTF-8")));
				StringBuilder dataJson = new StringBuilder();
				String lineText = null;
				while ((lineText = dataReader.readLine()) != null) {
					dataJson.append(lineText);
				}
				dataReader.close();
				JSONObject jsonMap = JSON.parseObject(dataJson.toString());
				if (jsonMap != null) {
					for (Map.Entry<String, Object> itemEntry : jsonMap.entrySet()) {
						String key = itemEntry.getKey();
						Object val = itemEntry.getValue();
						if (key.equals("cookieStore")) {
							val = toCookieStore((JSONObject) val);
						}
						if (val != null) {
							dataMap.put(key, val);
						}
					}
					logger.info("已从文件 " + this.dataFilePath + "加载数据");
				}
			}
			//
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T get(String key) {
		return (T) dataMap.get(key);
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T get(String key, T ifNullValue) {
		T value = (T) dataMap.get(key);
		return value == null ? ifNullValue : value;
	}

	@Override
	public void set(String key, Object value) {
		dataMap.put(key, value);
	}

	@Override
	public boolean save() {
		try {
			File dataFile = new File(this.dataFilePath);
			if (!dataFile.exists()) {
				dataFile.createNewFile();
			}
			BufferedWriter dataWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(dataFile), Charset.forName("UTF-8")));
			String dataJson = JSON.toJSONString(this.dataMap);
			dataWriter.write(dataJson);
			dataWriter.close();
			//
			logger.info("已把数据保存到文件 " + this.dataFilePath);
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}

	@Override
	public Map<String, Object> getAll() {
		return this.dataMap;
	}

}
