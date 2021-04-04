package com.unclezs.jfx.launcher;

/**
 * 操作系统枚举
 *
 * @author blog.unclezs.com
 * @since 2021/03/23 13:48
 */
public enum Platform {
  /**
   * Mac
   */
  MAC,
  /**
   * Linux
   */
  LINUX,
  /**
   * Windows
   */
  WIN,
  /**
   * 通用
   */
  COMMON;
  /**
   * 当前操作系统
   */
  public static final Platform CURRENT;

  public static final String MAC_STR = "mac";
  public static final String DARWIN = "darwin";
  public static final String NUX = "nux";
  public static final String WIN_STR = "win";

  static {
    String os = System.getProperty("os.name", " ").toLowerCase();

    if ((os.contains(MAC_STR)) || (os.contains(DARWIN))) {
      CURRENT = MAC;
    } else if (os.contains(NUX)) {
      CURRENT = LINUX;
    } else if (os.contains(WIN_STR)) {
      CURRENT = WIN;
    } else {
      CURRENT = COMMON;
    }
  }

  /**
   * 从字符串中读取OS
   *
   * @param osStr 字符串
   * @return OS
   */
  public static Platform fromString(String osStr) {
    Platform platform = null;
    if (containsIgnoreCase(osStr, WIN.name())) {
      platform = Platform.WIN;
    } else if (containsIgnoreCase(osStr, MAC.name())) {
      platform = Platform.MAC;
    } else if (containsIgnoreCase(osStr, LINUX.name())) {
      platform = Platform.LINUX;
    }
    return platform;
  }

  /**
   * 包含并且忽略大小写
   *
   * @param str     字符串
   * @param testStr 目标字符串
   * @return true 包含
   */
  private static boolean containsIgnoreCase(CharSequence str, CharSequence testStr) {
    if (null == str) {
      return null == testStr;
    }
    return str.toString().toLowerCase().contains(testStr.toString().toLowerCase());
  }
}
