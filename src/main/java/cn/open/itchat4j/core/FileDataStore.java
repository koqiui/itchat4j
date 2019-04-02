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
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSON;
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
						if (Core.isNickNameUserKey(key) || key.equals("nickSelf")) {// MsgUser
							val = CoreDataStore.toMsgUser((JSONObject) val);
						} else if (key.equals("cookieStore")) {// CookieStore
							val = CoreDataStore.toCookieStore((JSONObject) val);
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
	public void del(String key) {
		dataMap.remove(key);
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
