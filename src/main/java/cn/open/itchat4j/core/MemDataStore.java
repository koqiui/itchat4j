package cn.open.itchat4j.core;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MemDataStore implements CoreDataStore {
	private static final long serialVersionUID = 1L;
	private Logger logger = LoggerFactory.getLogger(this.getClass().getName());
	//
	private Map<String, Object> dataMap = new HashMap<String, Object>();

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
		return false;
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
		return false;
	}

	@Override
	public Map<String, Object> getAll() {
		return this.dataMap;
	}

}
