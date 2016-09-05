# LogAgent 预处理模块

## 1. 概述

LogAgent 是用于将后端数据实时导入到 Sensors Analytics 的工具。

默认情况下，LogAgent 读取的数据内容应当符合 Sensors Analytics 数据格式定义，相关定义请参考：

https://www.sensorsdata.cn/manual/data_schema.html

若希望使用 LogAgent 实时导入其他格式的数据，就需要开发自己的 LogAgent 数据预处理模块(Java)，预处理流程大致如下：

```
              自定义数据预处理模块
    原始数据 =====================> 符合 Sensors Analytics 的数据格式定义的数据
```

一个例子，比如原始数据是 Nginx 的 `access_log`，内容如下：

```
123.123.123.123 - - [03/Sep/2016:15:45:28 +0800] "GET /index.html HTTP/1.1" 200 396 "http://www.baidu.com/test" "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_11_5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/52.0.2743.116 Safari/537.36"
```

经过 **自定义数据预处理模块** 处理，得到 **符合 Sensors Analytics 数据格式定义的数据**，内容如下：

```
[{"distinct_id":"123.123.123.123","time":1472888728000,"type":"event","event":"RawPageView","properties":{"referrer":"http://www.baidu.com/test","status_code":"200","body_bytes_sent":396,"$ip":"123.123.123.123","$user_agent":"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_11_5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/52.0.2743.116 Safari/537.36","request_line":"GET /index.html HTTP/1.1"}},{"distinct_id":"123.123.123.123","time":1472893665131,"type":"profile_set","properties":{"from_baidu":true}}]
```

`src` 中为一个样例，功能是解析 Nginx 日志并生成事件和用户属性。

## 2. 开发方法

自定义 Java 类，并实现 `com.sensorsdata.analytics.tools.logagent.Processor` 接口即可。

接口定义如下：

```java
package com.sensorsdata.analytics.tools.logagent;

public interface Processor {
  String process(String line) throws Exception;
}
```

* `参数 String line`: LogAgent 读到的原始数据；
* `返回值`: 处理后得到的 Json 数组，数组元素为 **符合 SensorsAnalytics 的数据格式定义的数据**；

## 3. 配置方法

1. 编译 Java 代码并打包。以样例为例子：

   ```bash
   javac -cp log_agent-1.0-SNAPSHOT.jar cn/kbyte/CustomProcessor.java
   jar cvf custom_processor.jar cn/kbyte/CustomProcessor.class
   ```
   
   log_agent-1.0-SNAPSHOT.jar 可在 LogAgent 部署包中的 `lib` 目录下找到。

2. 将 jar 放到 LogAgent 的 `lib` 目录下。

3. 在配置文件中设置如下配置：

   ```
   # 数据预处理模块, 详见 LogAgent 文档
   processor=cn.kbyte.CustomProcessor
   ```

4. 测试模块是否生效，可以使用 ConsoleConsumer 仅将数据输出到标准输出而不发送导入，即运行：

   ```bash
   bin/logagent --sender ConsoleSender
   ```
   
   屏幕上会输出处理后的数据。

5. 启动 LogAgent，导入数据

## 4. 其他

* 如果数据无效，可以直接返回 `null`，LogAgent 会跳过这条数据；
* `process` 抛异常会将错误数据打印到日志中；

