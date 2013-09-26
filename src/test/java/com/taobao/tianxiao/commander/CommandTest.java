package com.taobao.tianxiao.commander;

import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

/**
 * Created with IntelliJ IDEA.
 *
 * @author tianxiao
 * @version 2013-09-26 17:32
 */
public class CommandTest {
    public static class NullCommand extends AbstractCommand {
    public NullCommand(int version, byte[] content) {
        super(version, content);
    }
}


    public class TestCommand extends AbstractCommand {
        public TestCommand() {
            super(1, "tianxiao".getBytes());
        }

        public TestCommand(int version, byte[] bytes) {
            super(version, bytes);
        }
    }

    @Test
    public void testCreateInnerClass() throws NoSuchMethodException {
        System.out.println(NullCommand.class.getName());
        System.out.println(NullCommand.class.isMemberClass());
        System.out.println(Modifier.isStatic(NullCommand.class.getModifiers()));

        Constructor<?> constructors = TestCommand.class.getConstructor(CommandTest.class, int.class, byte[].class);
    }

    @Test
    public void test() throws IOException {
        byte[] bytes = (new NullCommand(1, null)).serialize();
        NullCommand nullcommand = Command.deserialize(bytes);

        bytes = new TestCommand().serialize();
        TestCommand testCommand = Command.deserialize(bytes);
        System.out.println(new String(testCommand.getContent()));
    }

    @Test
    public void test1() throws IOException {
        byte[] bytes = (new HeartBeatCommand()).serialize();
        HeartBeatCommand command = Command.deserialize(bytes);
    }

    @Test
    public void test2() throws IOException {
        byte[] bytes = (new EchoCommand("127.0.0.1".getBytes())).serialize();
        EchoCommand command = Command.deserialize(bytes);

        System.out.println(new String(command.content));
    }
}
