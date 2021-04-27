package com.unclezs.jfx.launcher;

import lombok.Getter;

import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 模块化自定义加载模块辅助工具
 *
 * @author blog.unclezs.com
 * @date 2021/4/8 22:38
 */
@Getter
public class ModuleLoader {
  /**
   * 模块路径列表
   */
  private final Path[] modulePath;
  /**
   * 根模块
   */
  private final String rootModule;
  /**
   * 根模块的类加载器
   */
  private final ClassLoader classLoader;
  /**
   * 非本layer的模块的控制器列表
   */
  private final Map<ModuleLayer, ModuleLayer.Controller> controllers = new HashMap<>();
  /**
   * 模块所属的layer
   */
  private ModuleLayer layer;
  /**
   * 模块的控制器
   */
  private ModuleLayer.Controller controller;

  public ModuleLoader(Path[] modulePath, String rootModule) {
    this.modulePath = modulePath;
    this.rootModule = rootModule;
    defineModuleLayer();
    this.classLoader = layer.findLoader(rootModule);
  }

  /**
   * 根据传入的path列表及根模块定义layer
   */
  private void defineModuleLayer() {
    ModuleFinder finder = ModuleFinder.of(this.modulePath);
    ModuleLayer parentLayer = ModuleLayer.boot();
    Configuration configuration = parentLayer.configuration();
    Configuration appConfiguration = configuration.resolve(finder, ModuleFinder.ofSystem(), Set.of(this.rootModule));
    this.controller = ModuleLayer.defineModulesWithOneLoader(appConfiguration, List.of(parentLayer), ModuleLoader.class.getClassLoader());
    this.layer = this.controller.layer();
  }

  /**
   * 查找模块
   *
   * @param name 模块名
   * @return 模块
   */
  public Module findModule(String name) {
    Optional<Module> module = layer.findModule(name);
    if (module.isEmpty()) {
      throw new LauncherException(String.format("module not found! [layer=%s,module=%s]", layer, name));
    }
    return module.get();
  }

  /**
   * 让目标模块可以直接访问源模块 --add-exports 、exports
   *
   * @param source      源模块
   * @param packageName 包名
   * @param targets     目标模块
   */
  public void addExports(String source, String packageName, String... targets) {
    Module sourceModule = findModule(source);
    for (String target : targets) {
      getController(sourceModule).addExports(sourceModule, packageName, findModule(target));
    }
  }

  /**
   * 让目标模块可以反射访问源模块 --add-opens 、opens
   *
   * @param source      源模块
   * @param packageName 包名
   * @param targets     目标模块
   */
  public void addOpens(String source, String packageName, String... targets) {
    Module sourceModule = findModule(source);
    for (String target : targets) {
      getController(sourceModule).addOpens(sourceModule, packageName, findModule(target));
    }
  }

  /**
   * 添加可以访问 --add-reads 、requires
   *
   * @param source  源模块
   * @param targets 目标模块
   */
  public void addReads(String source, String... targets) {
    Module sourceModule = findModule(source);
    for (String target : targets) {
      getController(sourceModule).addReads(sourceModule, findModule(target));
    }
  }

  /**
   * 使用VM参数语句添加
   *
   * <pre>
   * --add-exports=java.base/sun.util.logging=com.a.test,com.a.prime
   * --add-opens=java.base/sun.util.logging=com.a.test,com.a.prime
   * --add-reads=java.base=com.a.test,com.a.prime
   * </pre>
   *
   * @param statement 表达式
   */
  public void add(String statement) {
    String[] expression = statement.split("=");
    String operation = expression[0];
    String[] targets = expression[2].split(",");
    String[] sourceModule = expression[1].split("/");
    String source = sourceModule[0];
    switch (operation) {
      case "--add-exports":
        addExports(source, sourceModule[1], targets);
        break;
      case "--add-opens":
        addOpens(source, sourceModule[1], targets);
        break;
      case "--add-reads":
        addReads(source, targets);
        break;
      default:
    }
  }


  /**
   * 获取模块Layer的控制器
   *
   * <pre>
   * --add-modules ALL-SYSTEM
   * --add-opens=java.base/java.lang=com.unclezs.jfx.launcher
   * </pre>
   *
   * @param module 模块
   * @return 控制器
   */
  private ModuleLayer.Controller getController(Module module) {
    if (module.getLayer() != layer) {
      ModuleLayer moduleLayer = module.getLayer();
      ModuleLayer.Controller moduleLayerController = controllers.get(moduleLayer);
      if (moduleLayerController == null) {
        try {
          Constructor<ModuleLayer.Controller> constructor = ModuleLayer.Controller.class.getDeclaredConstructor(ModuleLayer.class);
          constructor.setAccessible(true);
          moduleLayerController = constructor.newInstance(moduleLayer);
          controllers.put(moduleLayer, moduleLayerController);
        } catch (Exception error) {
          throw new LauncherException("反射创建Layer的控制器失败", error);
        }
      }
      return moduleLayerController;
    }
    return controller;
  }
}
