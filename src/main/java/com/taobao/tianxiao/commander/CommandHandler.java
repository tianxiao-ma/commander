package com.taobao.tianxiao.commander;

/**
 * Created with IntelliJ IDEA.
 *
 * @author tianxiao
 * @version 2013-09-26 17:04
 */
public interface CommandHandler {
    public boolean canHandle(Command command);
    public void handle(Command command);
}
