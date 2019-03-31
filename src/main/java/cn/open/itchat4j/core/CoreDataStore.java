package cn.open.itchat4j.core;

import java.io.Serializable;
import java.util.Map;

public interface CoreDataStore extends Serializable {

	void clear();

	void init(Map<String, Object> initValues);

	boolean load();

	<T> T get(String key);

	<T> T get(String key, T ifNullValue);

	void set(String key, Object value);

	boolean save();

	Map<String, Object> getAll();
}
