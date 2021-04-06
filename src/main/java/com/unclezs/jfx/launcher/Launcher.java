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
import java.net.URLClassLoader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.cert.X509Certificate;
import java.util.List;
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
    LOG.info(String.format("当前系统编码格式：file.encoding = %s ; default-charset = %s", System.getProperty("file.encoding"), Charset.defaultCharset()));
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
    ignoreSslCertificate();
    syncManifest();
    ui.setPhase("正在初始化运行环境...");
    ClassLoader classLoader = loadLibraries();
    Class<?> appClass = classLoader.loadClass(manifest.getLauncherClass());
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
   * 同步manifest
   */
  public void syncManifest() {
    try {
      // 嵌入Jar包中的
      Manifest remoteManifest;
      try {
        LOG.info(String.format("获取远程配置文件，%s", manifest.remoteManifest()));
        ui.setPhase("正在检测是否有新版本...");
        remoteManifest = Manifest.load(manifest.remoteManifest());
      } catch (Exception e) {
        LOG.warning(String.format("同步配置文件失败，%s \n %s", manifest.remoteManifest(), e.getMessage()));
        throw new RuntimeException(e);
      }
      if (!checkNew(remoteManifest)) {
        ui.setPhase(String.format("当前已是最新版本：%s", manifest.getVersion()));
        this.newVersion = false;
        return;
      }
      // 开始做更新
      ui.initUpdateView();
      ui.setPhase(String.format("检测到新版本：%s", manifest.getVersion()));
      manifest = remoteManifest;
      // 显示更新内容
      if (!manifest.getChangeLog().isEmpty()) {
        LOG.info(String.format("更新内容：%s", manifest.getChangeLog()));
        ui.setWhatNew(manifest.getChangeLog());
      }
      syncLibraries();
    } catch (Throwable t) {
      // 忽略更新失败
      LoggerHelper.error(LOG, "更新失败", t);
    }
  }

  /**
   * 加载本地 Manifest
   *
   * @throws Exception 加载失败
   */
  private void loadLocalManifest() throws Exception {
    LOG.info("解析本地配置文件");
    manifest = Manifest.embedded();
    Path localManifestPath = manifest.localManifest();
    // libDir下的
    if (Files.exists(localManifestPath)) {
      manifest = Manifest.load(localManifestPath.toUri());
    }
  }

  /**
   * 自定义Classloader加载依赖
   */
  private ClassLoader loadLibraries() {
    // 加载依赖
    List<URL> libs = manifest.resolveLibraries();
    URLClassLoader classLoader = new URLClassLoader(libs.toArray(new URL[0]));
    // 配置Classloader
    Thread.currentThread().setContextClassLoader(classLoader);
    FXMLLoader.setDefaultClassLoader(classLoader);
    FxUtils.runAndWait(() -> Thread.currentThread().setContextClassLoader(classLoader));
    return classLoader;
  }

  /**
   * 从远端同步文件到本地
   */
  private void syncLibraries() {
    try {
      ui.setPhase("正在同步最新版本配置...");
      Files.write(manifest.localManifest(), manifest.remoteManifest().toURL().openStream().readAllBytes());
      ui.setPhase("正在下载最新版本...");
      List<Library> libraries = manifest.resolveRemoteLibraries();
      ui.setProgress(0);
      double i = 0;
      for (Library library : libraries) {
        Path localPath = Path.of(library.getPath());
        if (!Files.exists(localPath) || Files.size(localPath) != library.getSize()) {
          // 创建父目录
          if (!Files.exists(localPath.getParent())) {
            Files.createDirectories(localPath.getParent());
          }
          // 下载更新
          URL url = library.toUrl(Path.of(URI.create(manifest.getUrl())));
          Files.write(localPath, url.openStream().readAllBytes());
          LOG.info(String.format("更新完成: %s", library.getPath()));
        }
        ui.setProgress(++i / libraries.size());
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
      for (Library library : remote.resolveRemoteLibraries()) {
        Path localPath = Path.of(library.getPath());
        if (!Files.exists(localPath) || Files.size(localPath) != library.getSize()) {
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
