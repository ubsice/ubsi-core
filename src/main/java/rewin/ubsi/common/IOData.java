package rewin.ubsi.common;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

/**
 * 接收或打包发送I/O数据
 */
public class IOData {

    /** Netty Pipeline数据解码器 */
    public static class Decoder extends ByteToMessageDecoder {
        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
            int n = in.readableBytes();
            if ( n == 0 )
                return;
            int index = in.readerIndex();
            int length = 0xff & in.getByte(index);
            if ( length < 128 ) {
                if ( n < 1 + length )
                    return;         // 还有数据未到达
                in.skipBytes(1);
            } else {
                // length的3~7位必须为"10101"作为标志，0~2位表示数据长度的字节数（不能超过4）
                if ((length & 0xF8) != 0xA8)
                    throw new Codec.DecodeException("invalid data");
                int size = length & 0x03;
                if (size > 4)
                    throw new Codec.DecodeException("data length overflow");
                if (n < 1 + size)
                    return;         // 还有数据未到达
                length = 0;
                for (int x = 0; x < size; x++)
                    length |= (0xff & in.getByte(index + 1 + x)) << (x * 8);
                if ( length < 0 )
                    throw new Codec.DecodeException("invalid data length");
                if (n < 1 + size + length)
                    return;         // 还有数据未到达
                in.skipBytes(1 + size);
            }
            if ( length == 0 )
                return;             // 没有有效数据（心跳：0x00）
            Object obj = Codec.decode(in);
            out.add(obj);
        }
    }

    /** 向Channel输出数据 */
    public static boolean write(Channel ch, Object obj) {
        ByteBuf body = Unpooled.directBuffer();
        Codec.encode(body, obj);

        int length = body.readableBytes();
        int size = 0;
        if ( length >= 128 ) {
            if (length < 256)
                size = 1;
            else if (length < 256 * 256)
                size = 2;
            else if (length < 256 * 256 * 256)
                size = 3;
            else
                size = 4;
        }
        byte[] buf = new byte[1+size];
        if ( length < 128 )
            buf[0] = (byte)length;
        else {
            buf[0] = (byte)(0xA8 | size);
            for ( int x = 0; x < size; x ++ )
                buf[1+x] = (byte)(length >> (x * 8));
        }

        ByteBuf head = Unpooled.wrappedBuffer(buf);
        ByteBuf data = Unpooled.wrappedBuffer(head, body);
        if ( !ch.isActive() )
            return false;
        ch.writeAndFlush(data);
        return true;
    }
}
