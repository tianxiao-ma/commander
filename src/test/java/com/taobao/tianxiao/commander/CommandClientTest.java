package com.taobao.tianxiao.commander;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Created with IntelliJ IDEA.
 *
 * @author tianxiao
 * @version 2013-09-26 17:30
 */
public class CommandClientTest {
    public static void main(String... args) throws InterruptedException, IOException {
        CommandClient client = new CommandClient();
        client.setServerHost("192.168.0.123");
        client.init();

        for(;;) {
            TimeUnit.SECONDS.sleep(60);
        }
    }
}
