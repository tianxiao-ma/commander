package com.taobao.tianxiao.commander;

import com.taobao.tianxiao.commander.util.ClassLoaderUtil;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * <pre>
 * the serialize format of Command is as following：
 *
 * 0_ _ _ _ 1_ _ _ 2_ _ _ _ _3 _ _ _ _ _ _ _ _ _
 * | version | type | reserved | content length |
 * |_ _ _ _ _|_ _ _ |_ _ _ _ _ | _ _ _ _ _ _ _ _|
 * |                total length                |
 * |_ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ |
 * |                                            |
 * |                                            |
 * |                 pay load                   |
 * |_ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ |
 * |                    foot                    |
 * |_ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ _ |
 *
 * payload contains command content, it start from the 9th byte, has &lt;content length&gt; bytes.
 * foot has &lt;total length&gt; - &lt;content lenght&gt; bytes
 *
 * </pre>
 *
 * @author tianxiao
 * @version 2013-09-26 17:01
 */
public abstract class Command {
    static final int HEAD_LENGTH = 8;
    static final int MAX_CONTENT_LENGTH = (1 << 8) - 1;
    protected static final int MASK = (1 << 8) - 1;
    protected static final String CHARSET = "UTF-8";
    protected static final byte EMPTY_BYTE = 0x00;

    protected byte[] content;
    protected int version;

    Command(int version, byte[] content) {
        this.version = version;
        this.content = content;
    }

    abstract byte[] serialize();
    abstract CommandType getCommandType();

    int contentLength() {
        return content == null ? 0 : content.length;
    }

    public int getVersion() {
        return version;
    }

    public byte[] getContent() {
        return content;
    }

    protected enum CommandType {
        HEART_BEAT(1), COMMAND(2);

        int type;

        private CommandType(int type) {
            this.type = type;
        }

        static CommandType fromType(int type) {
            CommandType rtn = null;
            switch (type) {
                case 1:
                    rtn = HEART_BEAT;
                    break;
                case 2:
                    rtn = COMMAND;
                    break;
                default:
            }

            return rtn;
        }
    }

    /**
     * 从<code>bytes</code>中解析出{@link Command}对象，并初始化。
     * <p>
     * 如果是{@link CommandType#COMMAND}类型的{@link Command}，会使用反射来创建{@link Command}，要求{@link Command}类必须必须继承自{@link AbstractCommand}, 并且具有<code>CommandObject(int, byte[])</code>形式的构造函数。
     * </p>
     * <p>
     * 支持以内部类形式定义的{@link Command}类，但是要求其Enclosing类必须具有无参构造函数。
     * </p>
     * @param bytes {@link Command}的序列化表示
     * @param <T> {@link Command}对象
     * @return
     * @throws IOException 反序列化失败抛出异常
     */
    @SuppressWarnings("unchecked")
    public static <T extends Command> T deserialize(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length < HEAD_LENGTH) {
            return null;
        }

        int version = bytes[0];
        CommandType commandType = CommandType.fromType(bytes[1]);
        if (commandType == null) {
            throw new IOException("unknown command type, type:" + bytes[1]);
        }
        int contentLength = MASK & bytes[3];
        if (bytes.length - HEAD_LENGTH < contentLength) {
            throw new IOException("not enough content bytes.");
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes, 4, 4);
        int totalLength = buffer.getInt();
        if (totalLength != bytes.length) {
            throw new IOException("length of bytes not equal to the length announced int the head.");
        }

        if (commandType == CommandType.HEART_BEAT) {
            return (T) new HeartBeatCommand();
        } else {
            // command class name is append after the content
            if (bytes.length <= HEAD_LENGTH + contentLength) {
                return null;
            }

            try {
                String className = new String(Arrays.copyOfRange(bytes, HEAD_LENGTH + contentLength, bytes.length), CHARSET);
                Class<T> clazz = (Class<T>) ClassLoaderUtil.loadClass(className);
                if (!AbstractCommand.class.isAssignableFrom(clazz)) {
                    return null;
                }

                Constructor<T> constructor;
                if (clazz.isMemberClass() && !Modifier.isStatic(clazz.getModifiers())) {
                    Class<?> enclosingClass = clazz.getEnclosingClass();
                    constructor = clazz.getConstructor(clazz.getEnclosingClass(), int.class, byte[].class);
                    return constructor.newInstance(enclosingClass.newInstance(), version, Arrays.copyOfRange(bytes, HEAD_LENGTH, HEAD_LENGTH + contentLength));
                } else {
                    constructor = clazz.getConstructor(int.class, byte[].class);
                    return constructor.newInstance(version, Arrays.copyOfRange(bytes, HEAD_LENGTH, HEAD_LENGTH + contentLength));
                }
            } catch (ClassNotFoundException e) {
                throw new IOException(e);
            } catch (NoSuchMethodException e) {
                throw new IOException(e);
            } catch (InvocationTargetException e) {
                throw new IOException(e);
            } catch (InstantiationException e) {
                throw new IOException(e);
            } catch (IllegalAccessException e) {
                throw new IOException(e);
            }
        }
    }
}
