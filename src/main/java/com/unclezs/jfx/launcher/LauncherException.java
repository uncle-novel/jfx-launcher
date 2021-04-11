package com.unclezs.jfx.launcher;

/**
 * @author blog.unclezs.com
 * @date 2021/4/11 0:32
 */
public class LauncherException extends RuntimeException{
  public LauncherException() {
  }

  public LauncherException(String message) {
    super(message);
  }

  public LauncherException(String message, Throwable cause) {
    super(message, cause);
  }
}
