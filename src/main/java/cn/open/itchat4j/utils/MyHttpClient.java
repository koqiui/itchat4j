package cn.open.itchat4j.utils;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.http.Consts;
import org.apache.http.HttpEntity;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CookieStore;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.cookie.Cookie;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;

import cn.open.itchat4j.core.HttpStoreHolder;
import cn.open.itchat4j.enums.UserAgentType;

/**
 * HTTP访问类，对Apache HttpClient进行简单封装，适配器模式
 * 
 * @author https://github.com/yaphone
 * @date 创建时间：2017年4月9日 下午7:05:04
 * @version 1.0
 *
 */
public class MyHttpClient {
	private static Logger logger = Logger.getLogger(MyHttpClient.class);

	//
	private HttpStoreHolder httpStoreHolder;
	private CloseableHttpClient httpClient;
	private CookieStore cookieStore;

	public MyHttpClient(HttpStoreHolder httpStoreHolder) {
		this.httpStoreHolder = httpStoreHolder;
		this.cookieStore = this.httpStoreHolder.getCookieStore();
		if (this.cookieStore == null) {
			throw new IllegalArgumentException("cookieStore 不能为 null");
		}
		this.httpClient = HttpClients.custom().setDefaultCookieStore(this.cookieStore).build();
	}

	public String getCookie(String name) {
		List<Cookie> cookies = cookieStore.getCookies();
		for (Cookie cookie : cookies) {
			if (cookie.getName().equalsIgnoreCase(name)) {
				return cookie.getValue();
			}
		}
		return null;
	}

	/**
	 * 处理GET请求
	 * 
	 * @author https://github.com/yaphone
	 * @date 2017年4月9日 下午7:06:19
	 * @param url
	 * @param params
	 * @return
	 */
	public HttpEntity doGet(String url, List<BasicNameValuePair> params, boolean redirect, Map<String, String> headerMap) {
		HttpEntity entity = null;
		try {
			HttpGet httpGet = new HttpGet();
			if (params != null) {
				String paramStr = EntityUtils.toString(new UrlEncodedFormEntity(params, Consts.UTF_8));
				httpGet = new HttpGet(url + "?" + paramStr);
			} else {
				httpGet = new HttpGet(url);
			}
			if (!redirect) {
				httpGet.setConfig(RequestConfig.custom().setRedirectsEnabled(false).build()); // 禁止重定向
			}
			UserAgentType userAgentType = UserAgentType.Win.name().equalsIgnoreCase(this.httpStoreHolder.getUserAgentType()) ? UserAgentType.Win : UserAgentType.Mac;
			httpGet.setHeader("User-Agent", UserAgentType.Win == userAgentType ? Config.USER_AGENT_WIN : Config.USER_AGENT_MAC);
			if (headerMap != null) {
				Set<Entry<String, String>> entries = headerMap.entrySet();
				for (Entry<String, String> entry : entries) {
					httpGet.setHeader(entry.getKey(), entry.getValue());
				}
			}
			CloseableHttpResponse response = httpClient.execute(httpGet);
			entity = response.getEntity();
		} catch (ClientProtocolException e) {
			logger.error(e.getMessage());
		} catch (IOException e) {
			logger.error(e.getMessage());
		} finally {
			this.httpStoreHolder.syncCookieStore(cookieStore);
		}

		return entity;
	}

	/**
	 * 处理POST请求
	 * 
	 * @author https://github.com/yaphone
	 * @date 2017年4月9日 下午7:06:35
	 * @param url
	 * @param params
	 * @return
	 */
	public HttpEntity doPost(String url, String paramsStr) {
		HttpEntity entity = null;
		try {
			HttpPost httpPost = new HttpPost();
			StringEntity params = new StringEntity(paramsStr, Consts.UTF_8);
			httpPost = new HttpPost(url);
			httpPost.setEntity(params);
			httpPost.setHeader("Content-type", "application/json; charset=utf-8");
			UserAgentType userAgentType = UserAgentType.Win.name().equalsIgnoreCase(this.httpStoreHolder.getUserAgentType()) ? UserAgentType.Win : UserAgentType.Mac;
			httpPost.setHeader("User-Agent", UserAgentType.Win == userAgentType ? Config.USER_AGENT_WIN : Config.USER_AGENT_MAC);
			CloseableHttpResponse response = httpClient.execute(httpPost);
			entity = response.getEntity();
		} catch (ClientProtocolException e) {
			logger.error(e.getMessage());
		} catch (IOException e) {
			logger.error(e.getMessage());
		} finally {
			this.httpStoreHolder.syncCookieStore(cookieStore);
		}

		return entity;
	}

	/**
	 * 上传文件到服务器
	 * 
	 * @author https://github.com/yaphone
	 * @date 2017年5月7日 下午9:19:23
	 * @param url
	 * @param reqEntity
	 * @return
	 */
	public HttpEntity doPostFile(String url, HttpEntity reqEntity) {
		HttpEntity entity = null;
		HttpPost httpPost = new HttpPost(url);
		UserAgentType userAgentType = UserAgentType.Win.name().equalsIgnoreCase(this.httpStoreHolder.getUserAgentType()) ? UserAgentType.Win : UserAgentType.Mac;
		httpPost.setHeader("User-Agent", UserAgentType.Win == userAgentType ? Config.USER_AGENT_WIN : Config.USER_AGENT_MAC);
		httpPost.setEntity(reqEntity);
		try {
			CloseableHttpResponse response = httpClient.execute(httpPost);
			entity = response.getEntity();
		} catch (Exception e) {
			logger.error(e.getMessage());
		} finally {
			this.httpStoreHolder.syncCookieStore(cookieStore);
		}
		return entity;
	}

}