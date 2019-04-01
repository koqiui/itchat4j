package cn.open.itchat4j.enums;

import java.util.EnumSet;
import java.util.Iterator;

/**
 * 便于别名区分
 * 
 * @author koqiui
 * @date 2019年4月1日 下午3:48:25
 *
 */
public enum MsgUserType {
	Friend(1, "普通朋友"), //
	Group(2, "群组"), //
	Public(3, "公众号"), //
	Special(4, "特殊号"), //
	Other(5, "其他");

	private int value;
	private String desc;

	private MsgUserType(int value, String desc) {
		this.value = value;
		this.desc = desc;
	}

	public int getValue() {
		return this.value;
	}

	public String getDesc() {
		return this.desc;
	}

	private static int[] allTypeValues;

	public static int[] getValues() {
		return allTypeValues;
	}

	static {
		EnumSet<MsgUserType> allElems = EnumSet.allOf(MsgUserType.class);
		allTypeValues = new int[allElems.size()];
		Iterator<MsgUserType> allElemIterator = allElems.iterator();
		int i = 0;
		while (allElemIterator.hasNext()) {
			allTypeValues[i] = allElemIterator.next().getValue();
		}
	}

}
