package com.taobao.tianxiao.commander;

import org.junit.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created with IntelliJ IDEA.
 *
 * @author tianxiao
 * @version 2013-09-26 17:27
 */
public class CommandServerTest {
    private static CommandServer server = new CommandServer();

    @Test
    public void test2() {
        System.out.println((1 << 31) - 1);
        System.out.println(Integer.MAX_VALUE);

        System.out.println((1 << 8) - 1);
    }

    @Test
    public void test() throws UnknownHostException, SocketException {
        InetAddress[] addresses = InetAddress.getAllByName("localhost");
        if (addresses == null || addresses.length == 0) {
            return;
        }

        String ip = null;
        Pattern ipv4 = Pattern.compile("[0-9]+.[0-9]+.[0-9]+.[0-9]+");
        for (InetAddress i : addresses) {
            if (i.isLoopbackAddress() || i.isMulticastAddress()) {
                continue;
            }

            Matcher matcher =ipv4.matcher(i.getHostAddress());
            if (matcher.find()) {
                ip = matcher.group();
                break;
            }
        }

        if (ip == null) {
            return;
        } else {
            System.out.println(ip);
        }
    }

    public static void main(String... args) throws IOException, InterruptedException {
        server.init();
        for (;;) {
            TimeUnit.SECONDS.sleep(10);
            if (server.hasClient()) {
                server.sendCommand(new EchoCommand("hello world".getBytes()));
            }
        }
    }
}
