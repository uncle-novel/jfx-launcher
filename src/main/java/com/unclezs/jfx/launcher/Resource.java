package com.unclezs.jfx.launcher;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;

/**
 * 更新资源
 *
 * @author blog.unclezs.com
 * @since 2021/03/23 13:46
 */
@Data
@NoArgsConstructor
public class Resource {

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

  public Resource(String path, Long size, Platform platform) {
    this.path = path;
    this.size = size;
    this.platform = platform;
  }

  public Resource(String path, Long size, Platform platform, Type type) {
    this.path = path;
    this.size = size;
    this.platform = platform;
    this.type = type;
  }

  public Resource(String path, Long size, Type type) {
    this.path = path;
    this.size = size;
    this.type = type;
  }

  public URL toUrl(Path libDir) {
    try {
      return libDir.resolve(path).toFile().toURI().toURL();
    } catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * 相对于app的路径
   *
   * @return 路径
   */
  public Path toLocalPath() {
    Path path = Path.of(this.path);
    if (path.isAbsolute()) {
      return path;
    }
    return Path.of(".", this.path).toAbsolutePath();
  }


  public boolean currentPlatform() {
    return platform == null || platform == Platform.CURRENT;
  }

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
