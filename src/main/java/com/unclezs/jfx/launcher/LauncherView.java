package com.unclezs.jfx.launcher;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.css.PseudoClass;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import lombok.extern.java.Log;

import java.util.List;
import java.util.logging.Level;

/**
 * 启动UI界面
 *
 * @author blog.unclezs.com
 * @since 2021/03/26 15:48
 */
@Log
public class LauncherView extends StackPane {

  public static final String DEFAULT_CLASS = "launcher-view";
  /**
   * 设置是否为更新的伪类
   */
  private static final PseudoClass UPDATING_PSEUDO_CLASS_STATE = PseudoClass.getPseudoClass("updating");
  private final Label phase = new Label();
  private final StackPane messageView = new StackPane();
  private final StackPane progressView = new StackPane();
  private final Manifest manifest;
  private ProgressBar progressBar;
  private Label whatNew;
  private ReadOnlyBooleanWrapper updating;

  public LauncherView(Manifest manifest) {
    this.manifest = manifest;
    getStyleClass().setAll(DEFAULT_CLASS);
    getStylesheets().setAll(LauncherView.class.getResource("/com/unclezs/jfx/launcher/css/view.css").toExternalForm());

    VBox container = new VBox();
    VBox.setVgrow(messageView, Priority.ALWAYS);
    container.getStyleClass().setAll("container");
    progressView.getStyleClass().setAll("progress-view");
    phase.getStyleClass().setAll("phase");

    container.getChildren().setAll(messageView, progressView);
    getChildren().addAll(container);

    initLauncherView();
  }

  /**
   * 初始化更新时组件
   */
  public void initUpdateView() {
    FxUtils.runAndWait(() -> {
      setUpdating(true);
      // 创建组件
      ScrollPane whatNewView = new ScrollPane();
      whatNew = new Label();
      progressBar = new ProgressBar();
      VBox box = new VBox(phase, progressBar);
      // 设置样式
      whatNew.getStyleClass().setAll("what-new");
      box.getStyleClass().setAll("progress-box");
      // 装载组件
      whatNewView.setContent(whatNew);
      messageView.getChildren().setAll(whatNewView);
      progressView.getChildren().setAll(box);
    });
  }

  /**
   * 初始化启动器组件
   */
  public void initLauncherView() {
    Label logo = new Label(manifest.getAppName());
    logo.getStyleClass().setAll("logo");
    messageView.getChildren().setAll(logo);
    progressView.getChildren().setAll(phase);
  }

  /**
   * 设置更新进度
   *
   * @param progress 进度
   */
  public void setProgress(double progress) {
    FxUtils.runFx(() -> this.progressBar.setProgress(progress));
  }

  /**
   * 设置本次更新的内容
   *
   * @param news 更新内容
   */
  public void setWhatNew(List<String> news) {
    StringBuilder updateMsg = new StringBuilder();
    for (String msg : news) {
      updateMsg.append(msg).append("\n");
    }
    FxUtils.runFx(() -> {
      ScrollPane whatNewView = new ScrollPane();
      whatNew.setText(updateMsg.toString());
      whatNewView.setContent(whatNew);
    });
  }

  /**
   * 设置错误提示
   *
   * @param e            异常信息
   * @param closeHandler 关闭按钮点击回调
   */
  public void setError(Throwable e, Runnable closeHandler) {
    log.log(Level.SEVERE, "程序启动异常", e);
    FxUtils.runFx(() -> {
      setUpdating(true);
      phase.setText("程序启动异常，请查看日志！");
      Pane exit = new Pane();
      // 处理退出按钮被点击
      exit.setOnMouseClicked(event -> {
        if (closeHandler == null) {
          System.exit(-1);
        }
        closeHandler.run();
      });
      VBox box = new VBox(phase, exit);
      exit.getStyleClass().add("exit-btn");
      phase.getStyleClass().add("error");
      box.getStyleClass().setAll("progress-box");
      progressView.getChildren().setAll(box);
    });
  }

  /**
   * 设置当前阶段
   *
   * @param phase 当前
   */
  public void setPhase(String phase) {
    log.info(phase);
    FxUtils.runAndWait(() -> this.phase.setText(phase));
  }

  public boolean getUpdating() {
    return updatingProperty().get();
  }

  public void setUpdating(boolean updating) {
    updatingPropertyImpl().set(updating);
  }

  public ReadOnlyBooleanProperty updatingProperty() {
    return updatingPropertyImpl().getReadOnlyProperty();
  }

  public ReadOnlyBooleanWrapper updatingPropertyImpl() {
    if (updating == null) {
      updating = new ReadOnlyBooleanWrapper(this, "updating", false) {
        @Override
        protected void invalidated() {
          pseudoClassStateChanged(UPDATING_PSEUDO_CLASS_STATE, get());
        }
      };
    }
    return updating;
  }
}
