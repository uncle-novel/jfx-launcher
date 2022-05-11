package com.unclezs.jfx.launcher;

import javafx.application.Platform;
import lombok.experimental.UtilityClass;
import lombok.extern.java.Log;

import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;

/**
 * Fx工具
 *
 * @author blog.unclezs.com
 * @date 2021/03/26 23:22
 */
@Log
@UtilityClass
public class FxUtils {

  /**
   * 运行在 FX
   *
   * @param runnable 可运行的
   */
  public static void runFx(Runnable runnable) {
    if (Platform.isFxApplicationThread()) {
      runnable.run();
    } else {
      Platform.runLater(runnable);
    }
  }

  /**
   * 运行和等待
   *
   * @param r r
   */
  public static void runAndWait(final Runnable r) {
    final CountDownLatch doneLatch = new CountDownLatch(1);
    runFx(() -> {
      try {
        r.run();
      } finally {
        doneLatch.countDown();
      }
    });
    try {
      doneLatch.await();
    } catch (InterruptedException e) {
      log.log(Level.WARNING, "runAndWait被中断", e);
      Thread.currentThread().interrupt();
    }
  }
}
