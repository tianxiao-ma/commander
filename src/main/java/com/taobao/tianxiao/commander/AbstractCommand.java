package com.taobao.tianxiao.commander;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

/**
 * Created with IntelliJ IDEA.
 *
 * @author tianxiao
 * @version 2013-09-26 17:03
 */
public abstract class AbstractCommand extends Command {
    public AbstractCommand(int version, byte[] content) {
        super(version, content);
    }

    @Override
    byte[] serialize() {
        String className = this.getClass().getName();
        try {
            byte[] classNameBytes = className.getBytes(CHARSET);
            int contentLength = contentLength();
            int totalLength = HEAD_LENGTH + contentLength + classNameBytes.length;

            ByteBuffer buffer = ByteBuffer.allocate(totalLength);
            buffer.put((byte) version);
            buffer.put((byte) getCommandType().type);
            buffer.put(EMPTY_BYTE);
            buffer.put((byte) contentLength);
            buffer.putInt(totalLength);
            if (contentLength > 0) {
                buffer.put(content);
            }
            buffer.put(classNameBytes);
            return buffer.array();
        } catch (UnsupportedEncodingException e) {
            // should not happen
            return null;
        }
    }

    @Override
    CommandType getCommandType() {
        return CommandType.COMMAND;
    }

    public static void main(String... args) {
        System.out.println(String.class.getName());
        System.out.println(byte.class.getName());
        System.out.println((new Object[3]).getClass().getName());
        System.out.println((new int[3][4][5][6][7][8][9]).getClass().getName());

        System.out.println("--------------");
        System.out.println(String.class.getCanonicalName());
        System.out.println(byte.class.getCanonicalName());
        System.out.println((new Object[3]).getClass().getCanonicalName());
        System.out.println((new int[3][4][5][6][7][8][9]).getClass().getCanonicalName());
    }
}
