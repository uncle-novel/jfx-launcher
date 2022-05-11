package com.unclezs.jfx.launcher;

/**
 * @author blog.unclezs.com
 * @date 2021/4/11 0:32
 */
public class LauncherException extends RuntimeException{
  /**
   * 启动器异常
   */
  public LauncherException() {
  }

  /**
   * 启动器异常
   *
   * @param message 消息
   */
  public LauncherException(String message) {
    super(message);
  }

  /**
   * 启动器异常
   *
   * @param message 消息
   * @param cause   原因
   */
  public LauncherException(String message, Throwable cause) {
    super(message, cause);
  }
}
