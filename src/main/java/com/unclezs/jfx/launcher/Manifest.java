package com.unclezs.jfx.launcher;

import com.google.gson.Gson;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author blog.unclezs.com
 * @date 2021/03/21 11:35
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Manifest {

  public static final Gson GSON = new Gson();

  /**
   * 嵌入Jar的配置文件名
   */
  public static final String EMBEDDED_CONFIG = "app.json";
  public static final String BACKSLASH = "/";
  /**
   * 配置文件位置
   */
  protected String configPath = EMBEDDED_CONFIG;
  /**
   * 应用 Logo
   */
  protected String appName = "FX-Launcher";
  /**
   * 服务器地址
   */
  protected String url;
  /**
   * 服务端配置的URI
   */
  protected String configUrl;
  /**
   * 版本
   */
  protected String version;
  /**
   * 更新内容
   */
  protected List<String> changeLog = new ArrayList<>();
  /**
   * 资源
   */
  protected List<Resource> resources = new ArrayList<>();
  /**
   * 启动类
   */
  protected String launchClass;
  /**
   * 启动模块
   */
  protected String launchModule;
  /**
   * 运行时导出/开放/读取的模块
   */
  protected List<String> moduleOptions = new ArrayList<>();
  /**
   * 是否为新版本
   */
  private transient boolean newVersion = true;

  /**
   * 加载配置
   *
   * @param uri 配置文件URI
   * @return 配置
   * @throws Exception 加载失败
   */
  @NonNull
  public static Manifest load(URI uri) throws Exception {
    try (InputStream stream = uri.toURL().openStream()) {
      return GSON.fromJson(new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)), Manifest.class);
    }
  }

  /**
   * 获取嵌入的 manifest
   *
   * @return manifest
   * @throws Exception /
   */
  public static Manifest embedded() throws Exception {
    URL resource = Launcher.class.getResource(BACKSLASH.concat(Manifest.EMBEDDED_CONFIG));
    if (resource == null) {
      return new Manifest();
    }
    return load(resource.toURI());
  }

  /**
   * 设置 服务器地址 保证 /结尾
   *
   * @param url 文件服务器地址
   */
  public void setUrl(String url) {
    if (!url.endsWith(BACKSLASH)) {
      url = url.concat(BACKSLASH);
    }
    this.url = url;
  }

  /**
   * 解析当前平台的资源
   *
   * @return 当前资源列表
   */
  public List<Resource> resolveResources() {
    return resources.stream().filter(Resource::currentPlatform).collect(Collectors.toList());
  }


  /**
   * 获取 libDir下的配置
   *
   * @return 配置
   */
  public Path localManifest() {
    return Path.of(".", configPath).toAbsolutePath();
  }

  /**
   * 获取 远程的配置
   *
   * @return 配置
   */
  public URI remoteManifest() {
    if (configUrl == null || configUrl.isBlank()) {
      return URI.create(url.concat(configPath));
    }
    return URI.create(configUrl);
  }

  /**
   * 输出为JSON
   *
   * @return JSON字符串
   */
  public String toJson() {
    return GSON.toJson(this);
  }

  /**
   * 校验配置是否有效
   *
   * @return true 有效
   */
  public boolean validate() {
    return launchClass != null && !launchClass.isBlank();
  }
}
