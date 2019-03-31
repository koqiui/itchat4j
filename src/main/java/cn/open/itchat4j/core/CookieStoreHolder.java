package cn.open.itchat4j.core;

import org.apache.http.client.CookieStore;

public interface CookieStoreHolder {
	void saveCookieStore(CookieStore cookieStore);

	CookieStore getCookieStore();
}
