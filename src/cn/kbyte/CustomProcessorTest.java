package cn.kbyte;

import org.junit.Test;

/**
 * Created by fengjiajie on 16/9/3.
 */
public class CustomProcessorTest {

  @Test public void testProcess() throws Exception {
    String sample =
        "123.123.123.123 - - [03/Sep/2016:15:45:28 +0800] \"GET /index.html HTTP/1.1\" 200 396 \"http://www.baidu.com/test\" \"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_11_5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/52.0.2743.116 Safari/537.36\"";

    CustomProcessor customProcessor = new CustomProcessor();
    String processResult = customProcessor.process(sample);

    System.out.println(processResult);
  }
}