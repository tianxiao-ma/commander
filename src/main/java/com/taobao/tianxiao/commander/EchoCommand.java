package com.taobao.tianxiao.commander;

/**
 * Created with IntelliJ IDEA.
 *
 * @author tianxiao
 * @version 2013-09-26 17:04
 */
final class EchoCommand extends AbstractCommand {
    public EchoCommand(byte[] content) {
        super(1, content);
    }

    public EchoCommand(int version, byte[] content) {
        super(version, content);
    }
}
