package com.taobao.tianxiao.commander;

/**
 * Created with IntelliJ IDEA.
 *
 * @author tianxiao
 * @version 2013-09-26 17:05
 */
final class HeartBeatCommand extends AbstractCommand {
    HeartBeatCommand() {
        super(1, null);
    }

    HeartBeatCommand(int version, byte[] content) {
        super(1, content);
    }

    @Override
    CommandType getCommandType() {
        return CommandType.HEART_BEAT;
    }
}
