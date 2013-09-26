package com.taobao.tianxiao.commander;

import com.taobao.tianxiao.commander.util.Constants;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * @author tianxiao
 * @version 2013-09-26 16:37
 */
public class CommandClient {
    private static final Log logger = LogFactory.getLog("CommandServerLog");
    // server ip
    private String serverHost;
    private Selector selector;
    private SelectionKey currentKey;
    private CopyOnWriteArrayList<CommandHandler> handlers;

    public CommandClient() {
    }

    public void addCommandHandler(CommandHandler handler) {
        handlers.add(handler);
    }

    public void setServerHost(String serverHost) {
        this.serverHost = serverHost;
    }

    public void init() throws IOException {
        if (serverHost == null) {
            throw new IllegalArgumentException("serverHost can't be null.");
        }

        handlers = new CopyOnWriteArrayList<CommandHandler>(new CommandHandler[]{new EchoCommandHandler()});
        Thread clientThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    startClient();
                } finally {
                    if (currentKey != null && currentKey.isValid()) {
                        try {
                            currentKey.channel().close();
                        } catch (IOException e) {
                            logger.error("close channel when client down failed!", e);
                        }
                        currentKey.cancel();
                    }

                    if (selector != null && selector.isOpen()) {
                        try {
                            selector.close();
                        } catch (IOException e) {
                            logger.error("close selector when client down failed!", e);
                        }
                    }
                }
            }
        });
        clientThread.setName("COMMAND-CLIENT");
        clientThread.start();
    }

    private void startClient() {
        try {
            selector = Selector.open();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        int reconnectCount = 0;
        OUTER:
        do {
            if (reconnectCount > 0) {
                int waitTime = 1 << Math.abs(reconnectCount);
                // wait 5 minute at most
                if (waitTime > 300) {
                    waitTime = 300;
                }
                try {
                    TimeUnit.SECONDS.sleep(waitTime);
                } catch (InterruptedException e) {
                    // do nothing
                }
            }

            SocketChannel channel;
            try {
                channel = doConnection();
            } catch (UnknownHostException e) {
                logger.warn("unknown command server, client down!", e);
                break;
            } catch (IOException e) {
                reconnectCount++;
                logger.warn("connect command server failed, reconnect.", e);
                continue;
            }

            try {
                currentKey = channel.register(selector, SelectionKey.OP_CONNECT);
            } catch (Throwable t) {
                logger.warn("register channel to selecter failed, client down!", t);
                try {
                    channel.close();
                } catch (IOException e) {
                    // do nothing
                }
                break;
            }

            int reselectCount = 0;
            RECONNECT:
            for (; ; ) {
                int selectedKeys;
                try {
                    selectedKeys = selector.select();
                } catch (IOException e) {
                    if (reselectCount >= 10) {
                        logger.warn("select fail to many times, reopen selector and reconnect!");
                        if (currentKey != null && currentKey.isValid()) {
                            try {
                                currentKey.channel().close();
                            } catch (IOException e1) {
                                // do nothing
                            }
                            currentKey.cancel();
                        }

                        try {
                            selector.close();
                        } catch (IOException e1) {
                            //do nothing
                        }

                        try {
                            selector = Selector.open();
                        } catch (IOException e1) {
                            logger.error("reopen selector failed, client down!", e1);
                            break OUTER;
                        }
                    } else {
                        logger.error("select failed, wait a moment and reselect.", e);
                        try {
                            TimeUnit.SECONDS.sleep(5);
                        } catch (InterruptedException e1) {
                            // do nothing
                        } finally {
                            reselectCount++;
                        }
                    }

                    continue;
                }

                if (selectedKeys > 0) {
                    Set<SelectionKey> keySet = selector.selectedKeys();
                    try {
                        for (Iterator<SelectionKey> it = keySet.iterator(); it.hasNext(); ) {
                            SelectionKey key = it.next();

                            SocketChannel socketChannel = (SocketChannel) key.channel();
                            if (key.isConnectable()) {
                                key.interestOps(0);
                                finishConnection(socketChannel);
                                key.interestOps(SelectionKey.OP_READ);
                                reconnectCount = 0;
                            } else if (key.isReadable()) {
                                key.interestOps(key.interestOps() & ~SelectionKey.OP_READ);
                                if (doRead(socketChannel, currentKey) == -1) {
                                    // server close the channel, process command
                                    ByteBuffer buffer = (ByteBuffer) currentKey.attachment();
                                    buffer.flip();
                                    byte[] commandBytes = new byte[buffer.limit()];
                                    buffer.get(commandBytes);

                                    processCommand(commandBytes);

                                    socketChannel.close();
                                    currentKey.cancel();
                                    break RECONNECT;
                                } else {
                                    key.interestOps(SelectionKey.OP_READ);
                                }
                            }
                            it.remove();
                        }
                    } catch (IOException t) {
                        if (currentKey != null) {
                            try {
                                currentKey.channel().close();
                            } catch (IOException e) {
                                // do nothing
                            }
                            currentKey.cancel();
                        }
                        logger.error("channel operation failed, reconnect to command server.", t);
                        reconnectCount++;
                        break;
                    }
                }
            }
        } while (true);
    }

    private void processCommand(byte[] commandBytes) {
        if (commandBytes == null || commandBytes.length == 0) {
            return;
        }

        ByteBuffer buffer = ByteBuffer.wrap(commandBytes);
        while (buffer.hasRemaining()) {
            int currentPosition = buffer.position();
            buffer.getInt(); // drop the first 4 bytes
            int length = buffer.getInt(); // get command length

            // message corrupted
            if (buffer.limit() < currentPosition + length) {
                break;
            }

            try {
                Command command = Command.deserialize(Arrays.copyOfRange(commandBytes, currentPosition, length));
                notifyHandler(command);
            } catch (IOException e) {
                logger.error(e);
            }
            buffer.position(currentPosition + length);
        }
    }

    private void notifyHandler(Command command) {
        for (CommandHandler handler : handlers) {
            if (handler.canHandle(command)) {
                handler.handle(command);
            }
        }
    }

    private void finishConnection(SocketChannel channel) throws IOException {
        channel.finishConnect();
        logger.warn("command server connected.");
    }

    private int doRead(SocketChannel channel, SelectionKey key) throws IOException {
        ByteBuffer buffer = (ByteBuffer) key.attachment();
        if (buffer == null) { // not yet register
            buffer = ByteBuffer.allocate(64);
            key.attach(buffer);
        } else {
            // scale read buffer
            if (!buffer.hasRemaining()) {
                ByteBuffer oldBuffer = buffer;
                oldBuffer.flip();

                buffer = ByteBuffer.allocate(oldBuffer.limit() << 1);
                buffer.put(oldBuffer);
                key.attach(buffer);
            }
        }

        return channel.read(buffer);
    }

    private SocketChannel doConnection() throws IOException {
        SocketChannel channel = SocketChannel.open();
        channel.configureBlocking(false);
        channel.connect(new InetSocketAddress(serverHost, Constants.PORT));
        return channel;
    }
}
