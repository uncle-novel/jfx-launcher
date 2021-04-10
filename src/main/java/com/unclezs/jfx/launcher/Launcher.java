package com.unclezs.jfx.launcher;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * 启动器
 *
 * @author blog.unclezs.com
 * @since 2021/02/27 19:46
 */
public class Launcher extends Application {

  private static final Logger LOG = LoggerHelper.get(Launcher.class);
  private Stage launcherStage;
  private Manifest manifest;
  private LauncherView ui;
  private boolean newVersion = true;

  public static void main(String[] args) {
    LOG.info("Start FX Launcher...");
    launch(args);
  }

  @Override
  public void init() throws Exception {
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
        manifest.setNewVersion(newVersion);
        Application app = (Application) appClass.getConstructor().newInstance();
        app.init();
        Stage appStage = new Stage();
        appStage.setUserData(manifest);
        ui.setPhase("正在启动应用...");
        app.start(appStage);
        launcherStage.close();
      } catch (Throwable e) {
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
    new Thread(() -> {
      try {
        startApplication();
      } catch (Throwable e) {
        handleStartError(e);
      }
    }).start();
  }

  /**
   * 检测更新
   *
   * @throws Exception 异常
   */
  private void checkForUpgrade() throws Exception {
    ignoreSslCertificate();
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
      LOG.info(String.format("获取远程配置文件，%s", manifest.remoteManifest()));
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
        LOG.info(String.format("更新内容：%s", manifest.getChangeLog()));
        ui.setWhatNew(manifest.getChangeLog());
      }
      return true;
    } catch (Throwable e) {
      // 忽略更新失败
      LoggerHelper.error(LOG, "更新失败", e);
    }
    return false;
  }

  /**
   * 加载本地 Manifest
   *
   * @throws Exception 加载失败
   */
  private void loadLocalManifest() throws Exception {
    LOG.info("解析本地配置文件");
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
      ui.setProgress(0);
      double i = 0;
      for (Resource resource : resources) {
        Path localPath = resource.toLocalPath();
        if (Files.notExists(localPath) || Files.size(localPath) != resource.getSize()) {
          // 创建父目录
          if (!Files.exists(localPath.getParent())) {
            Files.createDirectories(localPath.getParent());
          }
          // 下载更新
          URL url = resource.toUrl(Path.of(URI.create(manifest.getUrl())));
          Files.write(localPath, url.openStream().readAllBytes());
          LOG.info(String.format("更新完成: %s", resource.getPath()));
        }
        ui.setProgress(++i / resources.size());
      }
    } catch (Exception e) {
      LOG.warning(String.format("更新最新版本失败: %s", e.getMessage()));
      throw new RuntimeException(e);
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
      for (Resource resource : remote.resolveResources()) {
        Path localPath = resource.toLocalPath();
        if (Files.notExists(localPath) || Files.size(localPath) != resource.getSize()) {
          return true;
        }
      }
      return false;
    } catch (IOException e) {
      LOG.warning(String.format("检测是否有新版本失败: %s", e.getMessage()));
      throw new RuntimeException(e);
    }
  }

  /**
   * 忽略 SSL 错误
   *
   * @throws Exception /
   */
  private void ignoreSslCertificate() throws Exception {
    TrustManager[] trustManager = new TrustManager[]{
      new X509TrustManager() {
        @Override
        public void checkClientTrusted(X509Certificate[] x509Certificates, String s) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] x509Certificates, String s) {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
          return null;
        }
      }};
    SSLContext sslContext = SSLContext.getInstance("SSL");
    sslContext.init(null, trustManager, new java.security.SecureRandom());
    HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
    HostnameVerifier hostnameVerifier = (s, sslSession) -> true;
    HttpsURLConnection.setDefaultHostnameVerifier(hostnameVerifier);
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
      launcherStage.setOnCloseRequest(event -> Platform.exit());
      launcherStage.close();
    });
  }

}
