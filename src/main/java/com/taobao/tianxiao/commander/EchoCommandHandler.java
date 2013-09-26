package com.taobao.tianxiao.commander;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Created with IntelliJ IDEA.
 *
 * @author tianxiao
 * @version 2013-09-26 17:04
 */
public class EchoCommandHandler implements CommandHandler {
    private static final Log logger = LogFactory.getLog(EchoCommandHandler.class);

    public boolean canHandle(Command command) {
        return command != null && command.getClass() == EchoCommand.class;
    }

    @Override
    public void handle(Command command) {
        logger.warn("received echo command, command content:" + new String(command.getContent()));
    }
}
