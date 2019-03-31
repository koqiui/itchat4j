package cn.open.itchat4j.core;

import java.io.Serializable;
import java.util.Map;

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
}
