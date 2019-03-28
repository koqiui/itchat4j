package cn.open.itchat4j.utils;

import javax.swing.filechooser.FileSystemView;

/**
 * 
 * @author Hu Changwei
 * @date 2013-12-11
 * 
 *       功能：返回操作系统配置信息
 */
public class SysUtils {
	private SysUtils() {
		// prevent from being initialized
	}

	public static final String Key_os_name = "os.name";
	public static final String Key_os_arch = "os.arch";
	public static final String Key_os_version = "os.version";
	//
	public static final String Key_file_separator = "file.separator";
	public static final String Key_file_encoding = "file.encoding";
	public static final String Key_path_separator = "path.separator";
	public static final String Key_line_separator = "line.separator";

	private static final boolean isWindowsSys = isWindows();

	//
	public static final String Key_user_language = "user.language";
	public static final String Key_user_country = "user.country";
	public static final String Key_user_name = "user.name";
	public static final String Key_user_home = "user.home";

	public static String getOsName() {
		return System.getProperty(Key_os_name);
	}

	public static boolean isWindows() {
		return getOsName().toLowerCase().startsWith("windows");
	}

	public static String getOsArch() {
		return System.getProperty(Key_os_arch);
	}

	public static String getOsVersion() {
		return System.getProperty(Key_os_version);
	}

	public static String getFileSeparator() {
		return System.getProperty(Key_file_separator);
	}

	public static String getLineSeparator() {
		return System.getProperty(Key_line_separator);
	}

	public static String getFileEncoding() {
		return System.getProperty(Key_file_encoding);
	}

	public static String getPathSeparator() {
		return System.getProperty(Key_path_separator);
	}

	public static String getUserLanguage() {
		return System.getProperty(Key_user_language);
	}

	public static String getUserCountry() {
		return System.getProperty(Key_user_country);
	}

	public static String getUserName() {
		return System.getProperty(Key_user_name);
	}

	public static String getUserHome() {
		return System.getProperty(Key_user_home);
	}

	/* 用户桌面路径 */
	public static String getUserDesktop() {
		return FileSystemView.getFileSystemView().getHomeDirectory().getAbsolutePath();
	}

	public static <T> T selectByOs(T forWindows, T forOtherOs) {
		return isWindowsSys ? forWindows : forOtherOs;
	}

	private static final String os_suffix_win = ".win";
	private static final String os_suffix_lnx = ".lnx";

	// 返回系统后缀（主要用于多系统配置方便）
	public static String getOsSuffix() {
		return isWindowsSys ? os_suffix_win : os_suffix_lnx;
	}

	public static String getOsSuffixed(String rawStr) {
		return rawStr + (isWindowsSys ? os_suffix_win : os_suffix_lnx);
	}

	public static String getEnv(String varName) {
		return System.getenv(varName);
	}
}
