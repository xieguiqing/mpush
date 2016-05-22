package com.mpush.client.gateway;


import com.mpush.api.connection.Connection;
import com.mpush.api.protocol.Command;
import com.mpush.api.protocol.Packet;
import com.mpush.common.message.ErrorMessage;
import com.mpush.netty.connection.NettyConnection;
import com.mpush.client.push.PushRequest;
import com.mpush.client.push.PushRequestBus;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.mpush.common.ErrorCode.*;

/**
 * Created by ohun on 2015/12/19.
 *
 * @author ohun@live.cn
 */
@ChannelHandler.Sharable
public final class GatewayClientChannelHandler extends ChannelHandlerAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(GatewayClientChannelHandler.class);

    private Connection connection = new NettyConnection();

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        connection.updateLastReadTime();
        if (msg instanceof Packet) {
            Packet packet = ((Packet) msg);
            PushRequest request = PushRequestBus.INSTANCE.remove(packet.sessionId);
            if (request == null) {
                LOGGER.warn("receive a gateway response, but request timeout. packet={}", packet);
                return;
            }

            if (packet.cmd == Command.OK.cmd) {
                request.success();
            } else {
                ErrorMessage message = new ErrorMessage(packet, connection);
                if (message.code == OFFLINE.errorCode) {
                    request.offline();
                } else if (message.code == PUSH_CLIENT_FAILURE.errorCode) {
                    request.failure();
                } else if (message.code == ROUTER_CHANGE.errorCode) {
                    request.redirect();
                }
                LOGGER.warn("receive an error gateway response, message={}", message);
            }
        }
        LOGGER.info("receive msg:" + ctx.channel() + "," + msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        connection.close();
        LOGGER.error("caught an ex, channel={}", ctx.channel(), cause);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        LOGGER.info("client connect channel={}", ctx.channel());
        connection.init(ctx.channel(), false);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        connection.close();
        LOGGER.info("client disconnect channel={}", ctx.channel());
    }

    public Connection getConnection() {
        return connection;
    }
}