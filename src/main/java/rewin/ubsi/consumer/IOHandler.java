package rewin.ubsi.consumer;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import rewin.ubsi.common.IOData;
import rewin.ubsi.common.LogUtil;
import rewin.ubsi.common.Util;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Timer;
import java.util.concurrent.TimeUnit;

/**
 * UBSI客户端Socket I/O处理
 */
class IOHandler extends ChannelInboundHandlerAdapter {

    final static byte[] BEATHEART_DATA = new byte[] { 0 };      // 心跳数据

    String cause_msg = "socket channel closed";

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {	// 超时事件
        if ( evt instanceof IdleStateEvent ) {
            IdleStateEvent event = (IdleStateEvent)evt;
            if ( event.state().equals(IdleState.WRITER_IDLE) )
                ctx.writeAndFlush(Unpooled.wrappedBuffer(BEATHEART_DATA));
        } else
            super.userEventTriggered(ctx, evt);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        Connector.setChannelException(ctx.channel(), cause_msg);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        Object[] res = (Object[]) msg;
        String id = (String) res[0];
        byte code = (Byte) res[1];
        Object data = res[2];
        Map<String,Object> tailer = res.length > 3 ? (Map<String,Object>)res[3] : null;
        Connector.setChannelResponse(ctx.channel(), id, code, data, tailer);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        String addr = ((InetSocketAddress)ctx.channel().remoteAddress()).getAddress().getHostAddress();
        cause_msg = Util.getTargetThrowable(cause).toString();
        ctx.close();
        Context.log(LogUtil.ERROR, "channel@" + addr, cause);
    }

    static EventLoopGroup IOGroup = null;   // Consumer I/O处理线程池
    static Bootstrap IOBootstrap = null;    // Consumer 客户端I/O
    static Timer IOTimer = null;            // Consumer 定时任务

    // 初始化
    static void init() {
        if ( IOGroup == null ) {
            IOGroup = new NioEventLoopGroup(Context.IOThreads);
            IOBootstrap = new Bootstrap();
            IOBootstrap.group(IOGroup);
            IOBootstrap.channel(NioSocketChannel.class);
            IOBootstrap.option(ChannelOption.SO_KEEPALIVE, true);
            IOBootstrap.option(ChannelOption.TCP_NODELAY, true);
            IOBootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Context.TimeoutConnection * 1000);
            IOBootstrap.handler(new ChannelInitializer<SocketChannel>() {
                @Override
                public void initChannel(SocketChannel ch) throws Exception {
                    ch.pipeline().addLast(new IdleStateHandler(0, Context.BEATHEART_SEND, 0, TimeUnit.SECONDS))
                            .addLast(new IOData.Decoder(), new IOHandler());
                }
            });

            IOTimer = new Timer();
            IOTimer.schedule(new Connector.TimerDealer(), 0, Context.REGISTER_TIMER);
        }
    }

    // 结束
    static void close() {
        if ( IOGroup != null ) {
            IOTimer.cancel();
            IOTimer = null;

            IOGroup.shutdownGracefully();       // 关闭I/O线程池
            IOGroup = null;
            IOBootstrap = null;
        }
    }

    // 新建连接，连接失败会抛出异常
    static Channel connect(String host, int port) throws Exception {
        if ( IOBootstrap != null && host != null && !host.trim().isEmpty() ) {
            try {
                ChannelFuture f = IOBootstrap.connect(host, port).sync();
                Router.disableRegister(host.toLowerCase() + "#" + port, false);
                return f.channel();
            } catch (Exception e) {}
            Router.disableRegister(host.toLowerCase() + "#" + port, true);
        }
        throw new Context.ResultException(ErrorCode.CONNECT, "connect to " + host + "#" + port + " error");
    }
}
