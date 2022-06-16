package rewin.ubsi.container;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import rewin.ubsi.common.LogUtil;
import rewin.ubsi.consumer.ErrorCode;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;

/**
 * Socket的I/O处理、数据统计及状态管理
 */
class IOHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelActive(ChannelHandlerContext ctx) {      // 连接已经激活
        Bootstrap.SocketConnected.incrementAndGet();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {    // 连接已经关闭
        Bootstrap.SocketDisconnect.incrementAndGet();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {    // Socket超时事件通知
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent)evt;
            if ( event.state().equals(IdleState.READER_IDLE) )
                ctx.close();		// 超时未收到任何数据
        } else
            super.userEventTriggered(ctx, evt);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {    // 有数据到达
        ServiceContext sc = new ServiceContext(ctx.channel(), msg);
        // 限流
        if ( Bootstrap.RequestTotal.get() - Bootstrap.RequestDeal.get() >= Bootstrap.Overload ) {
            // 请求过载
            Bootstrap.RequestOverload.incrementAndGet();
            sc.setResult(ErrorCode.OVERLOAD, "server overload");
            sc.response();
            return;
        }
        ExecutorService workGroup = Bootstrap.WorkGroup;
        if ( workGroup == null || workGroup.isShutdown() ) {
            // 正在关闭
            sc.setResult(ErrorCode.SHUTDOWN, "server shutdown");
            sc.response();
            return;
        }
        // 提交给工作线程池进行处理
        WorkHandler worker = new WorkHandler(sc);
        workGroup.execute(worker);
        Bootstrap.RequestTotal.incrementAndGet();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {	// 连接异常
        String addr = ((InetSocketAddress)ctx.channel().remoteAddress()).getAddress().getHostAddress();
        ctx.close();
        Bootstrap.log(LogUtil.ERROR, "channel@" + addr, cause);
    }
}
