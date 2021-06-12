package com.unclezs.jfx.launcher;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import lombok.extern.java.Log;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * 启动器
 *
 * @author blog.unclezs.com
 * @since 2021/02/27 19:46
 */
@Log
public class Launcher extends Application {
  /**
   * 传递给app的信息，如果有新版本
   */
  public static final String CHANGE_LOG_ARG_NAME = "changeLog";
  public static final String VERSION_ARG_NAME = "version";
  public static final String HAS_NEW = "hasNew";
  private Stage launcherStage;
  private Manifest manifest;
  private LauncherView ui;
  private boolean newVersion = true;

  public static void main(String[] args) {
    launch(args);
  }

  @Override
  public void init() {
    Thread.currentThread().setName("Launcher");
    loadLocalManifest();
    ui = new LauncherView(manifest);
    ui.setPhase("正在检测更新...");
  }

  /**
   * 启动真正的应用
   *
   * @throws Exception 启动失败
   */
  public void startApplication() throws Exception {
    // 检测升级
    checkForUpgrade();
    ui.setPhase("正在初始化运行环境...");
    ClassLoader loader = loadLibraries();
    Class<?> appClass = loader.loadClass(manifest.getLaunchClass());
    if (!Application.class.isAssignableFrom(appClass)) {
      handleStartError(new IllegalArgumentException("启动类必须为Application的子类..."));
      return;
    }
    FxUtils.runFx(() -> {
      try {
        Application app = (Application) appClass.getConstructor().newInstance();
        app.init();
        Stage appStage = new Stage();
        appStage.setUserData(Map.of(CHANGE_LOG_ARG_NAME, manifest.getChangeLog(), VERSION_ARG_NAME, manifest.getVersion(), HAS_NEW, newVersion));
        ui.setPhase("正在启动应用...");
        app.start(appStage);
        launcherStage.close();
        launcherStage = null;
      } catch (Exception e) {
        handleStartError(e);
      }
    });
  }

  @Override
  public void start(Stage primaryStage) {
    launcherStage = primaryStage;
    launcherStage.setResizable(false);
    launcherStage.setScene(new Scene(ui, Color.TRANSPARENT));
    launcherStage.initStyle(StageStyle.TRANSPARENT);
    launcherStage.show();
    if (!manifest.validate()) {
      handleStartError(new IllegalArgumentException("配置文件格式错误！！"));
      return;
    }
    //noinspection AlibabaAvoidManuallyCreateThread
    Thread startThread = new Thread(() -> {
      try {
        startApplication();
      } catch (Exception e) {
        handleStartError(e);
      }
    });
    startThread.setDaemon(true);
    startThread.start();
  }

  /**
   * 检测更新
   */
  private void checkForUpgrade() {
    boolean hasNew = syncManifest();
    if (hasNew) {
      syncResources();
    }
  }

  /**
   * 同步manifest
   *
   * @return true 有更新
   */
  public boolean syncManifest() {
    try {
      log.log(Level.INFO, "获取远程配置文件:{0}", manifest.remoteManifest());
      ui.setPhase("正在检测是否有新版本...");
      Manifest remoteManifest = Manifest.load(manifest.remoteManifest());
      if (!checkNew(remoteManifest)) {
        ui.setPhase(String.format("当前已是最新版本：%s", manifest.getVersion()));
        this.newVersion = false;
        return false;
      }
      // 显示更新内容
      ui.initUpdateView();
      ui.setPhase(String.format("检测到新版本：%s", manifest.getVersion()));
      Path localManifest = manifest.localManifest();
      if (Files.notExists(localManifest)) {
        Files.createDirectories(localManifest.getParent());
      }
      Files.writeString(manifest.localManifest(), remoteManifest.toJson());
      manifest = remoteManifest;
      // 显示更新内容
      if (!manifest.getChangeLog().isEmpty()) {
        log.log(Level.INFO, "更新内容:{0}", manifest.getChangeLog());
        ui.setWhatNew(manifest.getChangeLog());
      }
      return true;
    } catch (Exception e) {
      // 忽略更新失败
      log.log(Level.SEVERE, "更新失败", e);
    }
    return false;
  }

  /**
   * 加载本地 Manifest
   */
  private void loadLocalManifest() {
    log.info("解析本地配置文件");
    manifest = Manifest.embedded();
    // 解析参数覆盖嵌入的
    parseParams();
    Path localManifestPath = manifest.localManifest();
    if (Files.exists(localManifestPath)) {
      manifest = Manifest.load(localManifestPath.toUri());
    }
  }

  /**
   * 自定义Classloader加载依赖
   */
  private ClassLoader loadLibraries() {
    List<Resource> resources = manifest.resolveResources();
    // 本地库
    resources.stream()
      .filter(resource -> Resource.Type.NATIVE == resource.getType())
      .map(resource -> Path.of(".", resource.getPath()).toFile().getAbsolutePath())
      .forEach(System::load);
    // 系统库
    resources.stream()
      .filter(resource -> Resource.Type.NATIVE_SYS == resource.getType())
      .map(Resource::getPath)
      .forEach(System::loadLibrary);
    // 加载依赖模块
    Path[] modules = resources.stream()
      .filter(resource -> Resource.Type.JAR == resource.getType())
      .map(Resource::toLocalPath)
      .toArray(Path[]::new);
    ModuleLoader moduleLoader = new ModuleLoader(modules, manifest.getLaunchModule());
    manifest.getModuleOptions().forEach(moduleLoader::add);
    ClassLoader classLoader = moduleLoader.getClassLoader();
    // 配置classloader
    FXMLLoader.setDefaultClassLoader(classLoader);
    FxUtils.runAndWait(() -> Thread.currentThread().setContextClassLoader(classLoader));
    Thread.currentThread().setContextClassLoader(classLoader);
    return classLoader;
  }

  /**
   * 从远端同步文件到本地
   */
  private void syncResources() {
    try {
      ui.setPhase("正在下载最新版本...");
      List<Resource> resources = manifest.resolveResources();
      final long totalSize = resources.stream().filter(Resource::hasNew).mapToLong(Resource::getSize).sum();
      ui.setProgress(0);
      double current = 0;
      for (Resource resource : resources) {
        if (resource.hasNew()) {
          Path localPath = resource.toLocalPath();
          // 创建父目录
          if (!Files.exists(localPath.getParent())) {
            Files.createDirectories(localPath.getParent());
          }
          // 下载更新
          URL url = resource.toUrl(manifest.getUrl());
          try (InputStream in = url.openStream(); OutputStream out = Files.newOutputStream(localPath)) {
            byte[] buffer = new byte[65536];
            int read;
            while ((read = in.read(buffer)) > -1) {
              out.write(buffer, 0, read);
              current += read;
              ui.setProgress(current / totalSize);
            }
          }
          log.log(Level.INFO, "更新完成: {0}", resource.getPath());
        }
      }
    } catch (Exception e) {
      throw new LauncherException("更新最新版本失败", e);
    }
  }

  /**
   * 检测是否有新版本
   *
   * @param remote 远程配置
   * @return true 有
   */
  private boolean checkNew(Manifest remote) {
    try {
      if (!manifest.equals(remote)) {
        return true;
      }
      return remote.resolveResources().stream().anyMatch(Resource::hasNew);
    } catch (Exception e) {
      throw new LauncherException("检测是否有新版本失败", e);
    }
  }

  /**
   * 初始化启动参数
   */
  private void parseParams() {
    Map<String, String> params = getParameters().getNamed();
    if (manifest == null) {
      manifest = new Manifest();
    }
    manifest.setAppName(params.getOrDefault("name", manifest.getAppName()));
    manifest.setUrl(params.getOrDefault("url", manifest.getUrl()));
    manifest.setConfigUrl(params.getOrDefault("configUrl", manifest.getConfigUrl()));
    manifest.setLaunchClass(params.getOrDefault("launchClass", manifest.getLaunchClass()));
    manifest.setLaunchModule(params.getOrDefault("launchModule", manifest.getLaunchModule()));
    manifest.setConfigPath(params.getOrDefault("configPath", manifest.getConfigPath()));
    manifest.setVersion(params.getOrDefault("version", manifest.getVersion()));
  }

  /**
   * 启动失败
   *
   * @param e 启动错误
   */
  private void handleStartError(Throwable e) {
    ui.setPhase("程序启动异常！！！");
    ui.setError(e, () -> {
      launcherStage.close();
      Platform.exit();
      System.exit(-1);
    });
  }

}
