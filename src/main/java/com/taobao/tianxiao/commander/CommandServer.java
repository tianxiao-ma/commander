package com.taobao.tianxiao.commander;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created with IntelliJ IDEA.
 *
 * @author tianxiao
 * @version 2013-09-26 16:37
 */
public class CommandServer {
    private static final Log logger = LogFactory.getLog("CommandServerLog");

    private static final int PORT = 7322;
    private Selector selector;
    // 已经建立连接，并且已经完成注册的客户端
    private Map<String, SelectionKey> registerClientMap = new ConcurrentHashMap<String, SelectionKey>();

    public CommandServer() {
    }

    public boolean hasClient() {
        return !registerClientMap.isEmpty();
    }

    public int numOfClients() {
        return registerClientMap.size();
    }

    public List<String> getClientIPs() {
        return new ArrayList<String>(registerClientMap.keySet());
    }

    private class Context {
        ByteBuffer writeBuffer = null;
        ConcurrentLinkedQueue<Command> commandQueue = new ConcurrentLinkedQueue<Command>();
    }

    public synchronized void sendCommand(Command command) {
        List<String> invalidClients = null;
        for (Map.Entry<String, SelectionKey> entry : registerClientMap.entrySet()) {
            if (entry.getValue() != null && entry.getValue().isValid()) {
                offerCommand(entry.getValue(), command);
            } else {
                if (invalidClients == null) {
                    invalidClients = new ArrayList<String>(5);
                }
                invalidClients.add(entry.getKey());
            }
        }

        if (invalidClients != null) {
            for (String client : invalidClients) {
                registerClientMap.remove(client);
            }
        }
        selector.wakeup();
    }

    private void offerCommand(SelectionKey key, Command command) {
        Context context = (Context) key.attachment();
        if (context == null) {
            context = new Context();
            key.attach(context);
        }
        context.commandQueue.offer(command);
        if ((key.interestOps() & SelectionKey.OP_WRITE) == 0) {
            key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
        }
    }

    public synchronized boolean sendCommandTo(String clientIp, Command command) {
        SelectionKey key = registerClientMap.get(clientIp);
        if (key != null && key.isValid()) {
            offerCommand(key, command);
            selector.wakeup();
            return true;
        } else {
            registerClientMap.remove(clientIp);
            return false;
        }
    }

    public void init() {
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(InetAddress.getLocalHost().getHostName());
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }

        if (addresses == null || addresses.length == 0) {
            throw new RuntimeException("no address found on localhost to start command server.");
        }

        String ip = null;
        Pattern ipv4 = Pattern.compile("[0-9]+.[0-9]+.[0-9]+.[0-9]+");
        for (InetAddress i : addresses) {
            if (i.isLoopbackAddress() || i.isMulticastAddress()) {
                continue;
            }

            Matcher matcher = ipv4.matcher(i.getHostAddress());
            if (matcher.find()) {
                ip = matcher.group();
                break;
            }
        }

        if (ip == null) {
            throw new RuntimeException("no valid address found on localhost to start command server.");
        }

        final String serverIp = ip;
        Thread serverThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    startServer(serverIp);
                } finally {
                    // close all the channel first, cause close selector will invalid all the register key
                    for (SelectionKey key : registerClientMap.values()) {
                        if (key != null && key.isValid()) {
                            try {
                                key.channel().close();
                            } catch (IOException e1) {
                                // do nothing
                            }
                            key.cancel();
                        }
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
        serverThread.setName("COMMAND-SERVER");
        serverThread.start();
    }

    private void startServer(String ip) {
        try {
            selector = Selector.open();
            ServerSocketChannel serverChannel = ServerSocketChannel.open();
            serverChannel.configureBlocking(false);
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);
            serverChannel.socket().bind(new InetSocketAddress(ip, PORT));
        } catch (IOException e) {

        }

        logger.warn("command server start at localhost(" + ip + ") on port:" + PORT);

        int reselectCount = 0;
        for (; ; ) {
            int selectKeys;
            try {
                selectKeys = selector.select();
            } catch (IOException e) {
                if (reselectCount >= 10) {
                    logger.warn("select fail to many times, reopen selector!");
                    for (SelectionKey key : registerClientMap.values()) {
                        if (key != null && key.isValid()) {
                            try {
                                key.channel().close();
                            } catch (IOException e1) {
                                // do nothing
                            }
                            key.cancel();
                        }
                    }

                    if (selector != null && selector.isOpen()) {
                        try {
                            selector.close();
                        } catch (IOException e1) {
                            logger.error("close selector when client down failed!", e1);
                        }
                    }

                    try {
                        selector = Selector.open();
                    } catch (IOException e1) {
                        logger.error("reopen selector failed, server down!", e1);
                        break;
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

            if (selectKeys > 0) {
                Set<SelectionKey> keySet = selector.selectedKeys();
                for (Iterator<SelectionKey> it = keySet.iterator(); it.hasNext(); ) {
                    SelectionKey key = it.next();
                    if (key.isAcceptable()) {
                        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
                        try {
                            accept(serverChannel);
                        } catch (Throwable t) {
                            logger.error("accept command client connection failed!", t);
                        }
                    } else if (key.isWritable()) {
                        key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
                        if (doWrite((SocketChannel) key.channel(), key)) {
                            key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
                        }
                    } else if (key.isReadable()) { // we need this to know client channel closure
                        key.interestOps(key.interestOps() & ~SelectionKey.OP_READ);
                        if (doRead((SocketChannel) key.channel(), key)) {
                            key.interestOps(key.interestOps() | SelectionKey.OP_READ);
                        }
                    } else {
                        // corrupted key
                        SelectableChannel channel = key.channel();
                        if (channel != null) {
                            try {
                                channel.close();
                            } catch (IOException e) {
                            }
                        }
                        key.cancel();
                        logger.warn("corrupted key found! ops:" + key.interestOps());
                    }

                    it.remove();
                }
            }

        }
    }

    private ByteBuffer discardBuffer = ByteBuffer.allocate(32);

    private boolean doRead(SocketChannel channel, SelectionKey key) {
        discardBuffer.clear();
        int discardBytes;
        try {
            discardBytes = channel.read(discardBuffer);
        } catch (IOException e) {
            try {
                channel.close();
            } catch (IOException e1) {
                // do nothing
            }
            key.cancel();
            logger.warn("read from channel failed, most likely client close the channel! client ip:" + channel.socket().getInetAddress().getHostAddress(), e);
            return false;
        }

        if (discardBytes == -1) {
            try {
                channel.close();
            } catch (IOException e) {
                // do nothing
            }
            key.cancel();
            logger.warn("client close the channel! client ip:" + channel.socket().getInetAddress().getHostAddress());
            return false;
        }

        return true;
    }

    private boolean doWrite(SocketChannel channel, SelectionKey key) {
        Context context = (Context) key.attachment();

        ByteBuffer buffer = context.writeBuffer;
        if (buffer != null && buffer.hasRemaining()) {
            try {
                channel.write(buffer);
            } catch (IOException e) {
                try {
                    channel.close();
                } catch (IOException e1) {
                    //do nothing
                }
                key.cancel();
                logger.error(e);
                return false;
            }
        }

        if (!context.commandQueue.isEmpty()) {
            Command command;
            List<byte[]> commandList = null;
            int totalCommandBytes = 0;

            while ((command = context.commandQueue.poll()) != null) {
                if (commandList == null) {
                    commandList = new ArrayList<byte[]>();
                }

                if (command.contentLength() > Command.MAX_CONTENT_LENGTH) {
                    logger.warn("command content length overflow,  must not greater than " + Command.MAX_CONTENT_LENGTH
                            + " bytes, command name:" + command.getClass().getSimpleName());
                    continue;
                }

                byte[] commandBytes = command.serialize();
                commandList.add(commandBytes);
                totalCommandBytes += commandBytes.length;
            }

            // if we have bytes left in current writebuffer, append to new buffer
            ByteBuffer oldBuffer = buffer;
            int oldBufferLength = 0;
            if (oldBuffer != null && (oldBufferLength = oldBuffer.flip().limit()) > 0) {
                buffer = ByteBuffer.allocate(totalCommandBytes + oldBufferLength);
            } else {
                buffer = ByteBuffer.allocate(totalCommandBytes);
            }

            context.writeBuffer = buffer;
            if (oldBufferLength > 0) {
                buffer.put(oldBuffer);
            }
            for (byte[] bytes : commandList) {
                buffer.put(bytes);
            }
            buffer.flip();

            try {
                channel.write(buffer);
            } catch (IOException e) {
                try {
                    channel.close();
                } catch (IOException e1) {
                    //do nothing
                }
                key.cancel();
                logger.error(e);
                return false;
            }
        }

        // we have written all the command, close the channel to tell client about this
        if (buffer != null && !buffer.hasRemaining()) {
            try {
                channel.close();
            } catch (IOException e1) {
                //do nothing
            }
            key.cancel();
            context.writeBuffer = null;
            return false;
        } else if (buffer != null) {
            return true;
        } else {
            return false;
        }
    }

    private SelectionKey accept(ServerSocketChannel serverChannel) throws IOException {
        SocketChannel channel = serverChannel.accept();

        try {
            channel.configureBlocking(false);
            SelectionKey key = channel.register(selector, SelectionKey.OP_READ);
            SelectionKey oldKey = registerClientMap.put(channel.socket().getInetAddress().getHostAddress(), key);
            if (oldKey != null) {
                try {
                    oldKey.channel().close();
                } catch (IOException e) {
                    // do nothing
                }
                oldKey.cancel();
            }
            logger.warn("receive new connection from host! host ip:" + channel.socket().getInetAddress().getHostAddress());
            return key;
        } catch (Throwable t) {
            logger.error("register socket channel to select failed! connection from:" + channel.socket().getInetAddress(), t);
            channel.close();
            return null;
        }
    }
}
