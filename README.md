# JFX-Launcher

<a href="https://github.com/unclezs/fx-launcher/actions/workflows/gradle.yml">
<img src="https://img.shields.io/github/workflow/status/unclezs/fx-launcher/Java%20CI%20with%20Gradle" alt="gradle build"/>
</a>
<a href="https://openjdk.java.net/">
<img src="https://img.shields.io/badge/version-1.0.8-blue" alt="版本"/>
</a>
<a href="https://github.com/unclezs/fx-launcher/blob/main/LICENSE">
<img src="https://img.shields.io/github/license/unclezs/fx-launcher?color=%2340C0D0&label=License" alt="GitHub license"/>
</a>
<a href="https://github.com/unclezs/fx-launcher/issues">
<img src="https://img.shields.io/github/issues/unclezs/fx-launcher?color=orange&label=Issues" alt="GitHub issues"/>
</a>
<a href="https://openjdk.java.net/">
<img src="https://img.shields.io/badge/OpenJDK-11-green" alt="JDK Version"/>
</a>

一个openJfx的自动更新器，采用模块化API加载模块。

<img src="https://gitee.com/unclezs/image-blog/raw/master/start.png" />
<img src="https://gitee.com/unclezs/image-blog/raw/master/20210409094616.png"/>

## 原理

在Launcher启动的时候，会对比本地配置与服务端配置是否一致，如果服务端配置与本地不一致，则进行拉取同步。 对比条件：

1. 版本号是否一致
2. 各个文件大小是否发生改变

同步完成之后，通过无需调用java指令再去启动，直接通过ModuleApi加载依赖模块，支持打破模块规则的参数， 如：add-exports、add-opens、add-reads可以在配置文件中进行设置

支持加载本地Native库，通过指定资源类型为 NATIVE、NATIVE_SYS 区分系统库与自定义库

## 简介

### 指定启动参数

可以通过程序的启动参数来运行启动器：

- --configPath=配置文件路径(conf/app.json) *
- --appName=应用名称 *
- --url=资源下载地址 *
- --launchClass=启动类 *
- --launchModule=启动类所属模块 *

### 配置介绍

- **url**： 资源下载地址
- **configPath**： 相对于url的配置路径
- **configUrl**： 直接指定配置全路径 ， 指定了将忽略configPath
- **appName**： 应用名称
- **version**： 版本号
- **changeLog**： 更新日志
- **launchModule**： 启动类
- **launchClass**： 启动类所属模块
- **moduleOptions**： 模块的一些打破规则的参数 ： add-exports、add-opens、add-reads
- **resources**: 资源列表，升级时候可以自动更新的，可以指定JAR、NATIVE、NATIVE_SYS、FILE类型的，根据不同类型采取不同的加载策略


