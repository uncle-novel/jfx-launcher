package com.unclezs.jfx.launcher;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 更新资源
 *
 * @author blog.unclezs.com
 * @since 2021/03/23 13:46
 */
@Data
@NoArgsConstructor
public class Resource implements Serializable {

  /**
   * 文件相对于URL的路径
   */
  private String path;
  /**
   * 文件大小
   */
  private Long size;
  /**
   * 操作系统、通用null
   */
  private Platform platform;
  /**
   * 文件类型，普通文件null
   */
  private Type type;

  /**
   * 资源
   *
   * @param path     路径
   * @param size     大小
   * @param platform 平台
   */
  public Resource(String path, Long size, Platform platform) {
    this.path = path;
    this.size = size;
    this.platform = platform;
  }

  /**
   * 资源
   *
   * @param path     路径
   * @param size     大小
   * @param platform 平台
   * @param type     类型
   */
  public Resource(String path, Long size, Platform platform, Type type) {
    this.path = path;
    this.size = size;
    this.platform = platform;
    this.type = type;
  }

  /**
   * 资源
   *
   * @param path 路径
   * @param size 大小
   * @param type 类型
   */
  public Resource(String path, Long size, Type type) {
    this.path = path;
    this.size = size;
    this.type = type;
  }

  /**
   * url
   *
   * @param baseUri 基础uri
   * @return {@link URL}
   */
  public URL toUrl(String baseUri) {
    try {
      return URI.create(baseUri).resolve(path).toURL();
    } catch (MalformedURLException e) {
      throw new LauncherException("格式错误的URL", e);
    }
  }

  /**
   * 相对于app的路径
   *
   * @return 路径
   */
  public Path toLocalPath() {
    Path localPath = Path.of(this.path);
    if (localPath.isAbsolute()) {
      return localPath;
    }
    return Path.of(".", this.path).toAbsolutePath();
  }

  /**
   * 是否更新了
   *
   * @return true 有更新
   */
  public boolean hasNew() {
    Path localPath = toLocalPath();
    return Files.notExists(localPath) || localPath.toFile().length() != size;
  }

  /**
   * 当前平台
   *
   * @return boolean
   */
  public boolean currentPlatform() {
    return platform == null || platform == Platform.CURRENT;
  }

  /**
   * 类型
   *
   * @author unclezs
   * @date 2022/05/11
   */
  public enum Type {
    /**
     * Jar包，classloader加载
     */
    JAR,
    /**
     * 自定义native库 System.load()
     */
    NATIVE,
    /**
     * 系统native库，会直接通过System.loadLibrary()
     */
    NATIVE_SYS
  }
}
