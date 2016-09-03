package cn.kbyte;

import com.sensorsdata.analytics.tools.logagent.Processor;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by fengjiajie on 16/9/3.
 */
public class CustomProcessor implements Processor {

  /**
   * 用于解析 Nginx 日志, 例如:
   * 123.123.123.123 - - [03/Sep/2016:15:45:28 +0800] "GET /index.html HTTP/1.1" 200 396 "http://p.kbyte.cn/" "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_11_5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/52.0.2743.116 Safari/537.36"
   */
  private static final String NGINX_LOG_PATTERN =
      "^([\\d.]+) (\\S+) (\\S+) \\[([\\w:/]+\\s[+\\-]\\d{4})\\] \"(.+?)\" (\\d{3}) (\\d+) \"([^\"]+)\" \"([^\"]+)\"";
  private Matcher nginxLogMatcher = Pattern.compile(NGINX_LOG_PATTERN).matcher("");

  /**
   * 用于解析时间, 例如:
   * 03/Sep/2016:15:45:28 +0800
   */
  private SimpleDateFormat timeLocalDateFormat =
      new SimpleDateFormat("dd/MMM/yyyy:HH:mm:ss Z", Locale.US);

  private ObjectMapper objectMapper = new ObjectMapper();

  /**
   * LogAgent 读到一行数据将调用该函数进行预处理, 返回值应为符合 https://www.sensorsdata.cn/manual/data_schema.html 格式的数据
   *
   * @param line 一行需要预处理的数据
   * @return 一个字符串, 内容为一个 Json 数组, 数组内的每个元素为一条有效数据
   * @throws Exception 可自定义异常, 抛出异常会在 LogAgent 日志中体现, 并跳过这条数据
   */
  @Override public String process(String line) throws Exception {
    nginxLogMatcher.reset(line);
    if (!nginxLogMatcher.find()) {
      // 这里抛出异常会被 LogAgent 捕获, LogAgent 日志中会输出这条数据以及栈信息并跳过这条数据
      throw new Exception("parse line error");
    }

    String remoteAddr = nginxLogMatcher.group(1);
    String timeLocal = nginxLogMatcher.group(4);
    String requestLine = nginxLogFieldUnEscape(nginxLogMatcher.group(5));
    String statusCode = nginxLogMatcher.group(6);
    String bodyBytesSent = nginxLogMatcher.group(7);
    String referrer = nginxLogFieldUnEscape(nginxLogMatcher.group(8));
    String userAgent = nginxLogFieldUnEscape(nginxLogMatcher.group(9));

    // 如果返回 null, 即认为这行数据无效, 例如忽略掉 GET favicon.ico 的请求
    if (requestLine.contains("favicon.ico")) {
      return null;
    }

    // 一次可以返回多条数据（可以是事件和用户属性）
    List<Map<String, Object>> records = new ArrayList<>();

    // 一条事件类型数据, 用于记录用户访问事件
    Map<String, Object> eventRecord = new HashMap<>();
    eventRecord.put("type", "track");
    // 假设以 ip 作为用户
    eventRecord.put("distinct_id", remoteAddr);
    // 解析 03/Sep/2016:15:45:28 +0800 作为时间发生时间
    eventRecord.put("time", timeLocalDateFormat.parse(timeLocal).getTime());
    // 事件名称为 RawPageView
    eventRecord.put("event", "RawPageView");
    // 事件相关属性
    Map<String, Object> eventRecordProperties = new HashMap<>();
    eventRecord.put("properties", eventRecordProperties);
    eventRecordProperties.put("request_line", requestLine);
    eventRecordProperties.put("status_code", statusCode);
    eventRecordProperties.put("body_bytes_sent", Integer.parseInt(bodyBytesSent));
    eventRecordProperties.put("referrer", referrer);
    // 设置 $user_agent, 可以自动解析
    eventRecordProperties.put("$user_agent", userAgent);
    // 设置 $ip, 可解析地理位置
    eventRecordProperties.put("$ip", remoteAddr);
    records.add(eventRecord);

    // 若用户来自百度(referrer 包含 baidu), 记录一个用户属性
    if (referrer.contains("baidu")) {
      Map<String, Object> profileRecord = new HashMap<>();
      profileRecord.put("type", "profile_set");
      profileRecord.put("distinct_id", remoteAddr);
      profileRecord.put("time", new Date().getTime());
      Map<String, Object> profileRecordProperties = new HashMap<>();
      profileRecord.put("properties", profileRecordProperties);
      profileRecordProperties.put("from_baidu", true);
      records.add(profileRecord);
    }

    return objectMapper.writeValueAsString(records);
  }

  public byte[] buffer = new byte[1024 * 1024];

  /**
   * 该函数用于字符串字段解码. nginx 输出日志时会对一些字符进行编码, 以避免冲突
   */
  public String nginxLogFieldUnEscape(String record) {
    int count = 0;
    for (int i = 0; i < record.length(); i++) {
      byte ch;
      if (record.charAt(i) == '\\' && record.charAt(i + 1) == 'x') {
        ch = (byte) Integer.parseInt(record.substring(i + 2, i + 4), 16);
        i += 3;
      } else {
        ch = (byte) record.charAt(i);
      }
      buffer[count++] = ch;
    }
    buffer[count] = 0;
    return new String(buffer, 0, count);
  }
}
