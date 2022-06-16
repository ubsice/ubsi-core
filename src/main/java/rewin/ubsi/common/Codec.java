package rewin.ubsi.common;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.bson.types.Binary;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.math.BigInteger;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Java数据类型编码解码器
 */
public class Codec {

    static final int NULL = 0;          // null
    static final int BOOL = 1;          // boolean -> Boolean
    static final int BYTE = 2;          // byte -> Byte
    static final int INT = 3;           // short/char/int -> Integer
    static final int LONG = 4;          // long -> Long
    static final int BIGINT = 5;        // BigInteger
    static final int DOUBLE = 6;        // float/double -> Double
    static final int BIGDEC = 7;        // BigDecimal
    static final int BYTES = 8;         // byte[]
    static final int STR = 9;           // CharSequence -> String
    static final int LIST = 10;         // List -> ArrayList
    static final int SET = 11;          // Set -> HashSet
    static final int ARR = 12;          // T[] -> Object[]
    static final int MAP = 13;          // Map/Class<?> -> HashMap
    static final int ID = 14;           // MongoDB ObjectID
    static final int PATTERN = 15;      // java.util.regex.Pattern，正则表达式

    /** 将Java对象打包到ByteBuf中，打包格式：数据类型 + [数据长度] + 数据，其中：
            数据类型：   1个byte，4~7位表示数据类型，0~3位表示：
                                NULL：   0
                                BOOL：   0:false，1:true
                                xxx：    <=7表示数据长度，>=8时，&7后表示数据长度的字节数，0~4
            数据长度：   0~4个字节，低字节在前，高字节在后
       支持的数据类型：
            boolean/byte
            short/char/int      转换为4字节int，低字节在前，高字节在后
            long                8个字节，低字节在前，高字节在后
            BigInteger          转换成string
            float/double        转换成string
            BigDecimal          转换成string
            ObjectId            转换成byte[]
            byte[]
            CharSequence        toString()后转换为utf-8格式的byte[]
            List/Set/T[]        转换为ArrayList/HashSet/Object[]
            Map/Class<?>        Value-Object对象，Class会转换为Map：只包含public的成员变量，且不能是static/final
     */
    public static void encode(ByteBuf buf, Object value) {
        if ( value == null )
            buf.writeByte(NULL);
        else if ( value instanceof Boolean )
            buf.writeByte((BOOL << 4) + ((Boolean)value ? 1 : 0));
        else if ( value instanceof Byte ) {
            buf.writeByte(BYTE << 4);
            buf.writeByte(0xff & ((Byte) value).byteValue());
        } else if ( value instanceof Short )
            putInt(buf, ((Short)value).intValue());
        else if ( value instanceof Character )
            putInt(buf, ((Character)value).charValue());
        else if ( value instanceof Integer )
            putInt(buf, ((Integer)value).intValue());
        else if ( value instanceof Long ) {
            buf.writeByte(LONG << 4);
            long l = ((Long)value).longValue();
            for ( int x = 0; x < 8; x ++ )
                buf.writeByte((int)((l >>> (x*8)) & 0xff));
        } else if ( value instanceof BigInteger )
            putValue(buf, BIGINT, value.toString());
        else if ( value instanceof Float )
            putValue(buf, DOUBLE, value.toString());
        else if ( value instanceof Double )
            putValue(buf, DOUBLE, value.toString());
        else if ( value instanceof BigDecimal )
            putValue(buf, BIGDEC, ((BigDecimal)value).toPlainString());
        else if ( value instanceof Throwable )
            putValue(buf, STR, Util.getTargetThrowable((Throwable)value).toString());
        else if ( value instanceof byte[] )
            putBytes(buf, BYTES, (byte[])value);
        else if ( value instanceof CharSequence )
            putValue(buf, STR, value.toString());
        else if ( value instanceof ObjectId )
            putBytes(buf, ID, ((ObjectId)value).toByteArray());
        else if ( value instanceof Pattern )
            putValue(buf, PATTERN, ((Pattern)value).pattern() + "/" + ((Pattern)value).flags());
        else if ( value instanceof Binary )
            putBytes(buf, BYTES, ((Binary)value).getData());
        else if ( value instanceof Decimal128 )
            putValue(buf, BIGDEC, ((Decimal128)value).bigDecimalValue().toPlainString());
        else if ( value.getClass().isArray() ) {
            int length = Array.getLength(value);
            putLength(buf, ARR, length);
            for ( int x = 0; x < length; x ++ )
                encode(buf, Array.get(value, x));
        } else if ( value instanceof List ) {
            int length = ((List)value).size();
            putLength(buf, LIST, length);
            for ( int x = 0; x < length; x ++ )
                encode(buf, ((List)value).get(x));
        } else if ( value instanceof Set ) {
            int length = ((Set)value).size();
            putLength(buf, SET, length);
            for ( Object x : (Set)value )
                encode(buf, x);
        } else if ( value instanceof Map ) {
            int length = ((Map)value).size();
            putLength(buf, MAP, length);
            for ( Object k : ((Map)value).keySet() ) {
                encode(buf, k);
                encode(buf, ((Map)value).get(k));
            }
        } else {
            Map<String,Object> map = obj2Map(value);    // 按Value-Object对象处理
            encode(buf, map);   // 转换为Map进行打包
        }
    }

    // 将int放入ByteBuf
    private static void putInt(ByteBuf buf, int i) {
        buf.writeByte(INT << 4);
        for ( int x = 0; x < 4; x ++ )
            buf.writeByte((i >>> (x*8)) & 0xff);
    }

    // 将type和string放入ByteBuf
    private static void putValue(ByteBuf buf, int type, String value) {
        byte[] bs;
        try {
            bs = value.getBytes("utf-8");
        } catch(Exception e) {
            bs = value.getBytes();
        }
        putBytes(buf, type, bs);
    }

    // 将type和string放入ByteBuf
    private static void putBytes(ByteBuf buf, int type, byte[] bs) {
        putLength(buf, type, bs.length);
        buf.writeBytes(bs);
    }

    // 将type和length放入ByteBuf
    private static void putLength(ByteBuf buf, int type, int length) {
        type <<= 4;
        if ( length <= 7 )
            buf.writeByte(type + length);
        else {
            int len = 1;
            if ( length >= 256 * 256 * 256 )
                len = 4;
            else if ( length >= 256 * 256 )
                len = 3;
            else if ( length >= 256 )
                len = 2;
            buf.writeByte(type + 8 + len);
            for ( int x = 0; x < len; x ++ )
                buf.writeByte((length >> (x * 8)) & 0xff);
        }
    }

    // 将Value-Object对象映射为Map
    static Map<String,Object> obj2Map(Object o) {
        Map<String,Object> res = new HashMap<String,Object>();
        for ( Field fd : o.getClass().getFields() ) {
            int mod = fd.getModifiers();
            if ( Modifier.isStatic(mod) || Modifier.isFinal(mod) )
                continue;
            try {
                res.put(fd.getName(), fd.get(o));
            } catch(Exception e ) {}
        }
        return res;
    }

    /** 将Java数据对象转换为UBSI支持的基础数据对象 */
    public static Object toObject(Object value) {
        // 处理UBSI数据类型
        if ( value == null ||
                value instanceof Boolean ||
                value instanceof Character ||
                value instanceof Number ||
                value instanceof BigInteger ||
                value instanceof BigDecimal ||
                value instanceof byte[] ||
                value instanceof CharSequence ||
                value instanceof ObjectId ||
                value instanceof Pattern)
            return value;
        if ( value instanceof Binary )
            return ((Binary)value).getData();
        if ( value instanceof Decimal128 )
            return ((Decimal128)value).bigDecimalValue();
        if ( value instanceof Throwable )
            return Util.getTargetThrowable((Throwable)value).toString();
        if ( value instanceof List ) {
            List res = new ArrayList();
            for ( Object x : (List)value )
                res.add(toObject(x));
            return res;
        }
        if ( value instanceof Set ) {
            Set res = new HashSet();
            for ( Object x : (Set)value )
                res.add(toObject(x));
            return res;
        }
        if ( value instanceof Map ) {
            Map res = new HashMap();
            for ( Object k : ((Map)value).keySet() )
                res.put(toObject(k), toObject(((Map)value).get(k)));
            return res;
        }
        Class cls = value.getClass();
        if ( cls.isArray() ) {
            if ( cls.getComponentType().isPrimitive() )
                return value;
            int length = Array.getLength(value);
            Object[] res = new Object[length];
            for ( int x = 0; x < length; x ++ )
                res[x] = toObject(Array.get(value, x));
            return res;
        }
        Map res = new HashMap();
        for ( Field fd : cls.getFields() ) {
            int mod = fd.getModifiers();
            if ( Modifier.isStatic(mod) || Modifier.isFinal(mod) )
                continue;
            try {
                Object v = fd.get(value);
                res.put(fd.getName(), toObject(v));
            } catch (Exception e) {}
        }
        return res;
    }

    /** 自定义异常 */
    static class DecodeException extends Exception {
        DecodeException(String msg) {
            super(msg);
        }
    }

    /** 从ByteBuf中解析Java对象 */
    public static Object decode(ByteBuf buf) throws Exception {
        byte type = buf.readByte();
        if ( type == 0 )
            return null;
        switch ( (0xff & type) >> 4 ) {
            case BOOL:
                return (type & 0x0f) == 0 ? false : true;
            case BYTE:
                return buf.readByte();
            case INT:
                int ires = 0;
                for ( int x = 0; x < 4; x ++ )
                    ires |= (0xff & buf.readByte()) << (x * 8);
                return ires;
            case LONG:
                long lres = 0;
                for ( int x = 0; x < 8; x ++ )
                    lres |= ((long)0xff & buf.readByte()) << (x * 8);
                return lres;
            case BIGINT:
                return new BigInteger(getString(buf, type));
            case DOUBLE:
                return Double.parseDouble(getString(buf, type));
            case BIGDEC:
                return new BigDecimal(getString(buf, type));
            case BYTES:
                return getBytes(buf, type);
            case STR:
                return getString(buf, type);
            case ID:
                return new ObjectId(getBytes(buf, type));
            case PATTERN: {
                String pstr = getString(buf, type);
                int index = pstr.lastIndexOf('/');
                int flags = Integer.parseInt(pstr.substring(index+1));
                return Pattern.compile(pstr.substring(0, index), flags);
            }
            case LIST:
                int lsize = getLength(buf, type);
                List<Object> list = new ArrayList<Object>();
                for ( int x = 0; x < lsize; x ++ )
                    list.add(decode(buf));
                return list;
            case SET:
                int ssize = getLength(buf, type);
                Set<Object> set = new HashSet<Object>();
                for ( int x = 0; x < ssize; x ++ )
                    set.add(decode(buf));
                return set;
            case ARR:
                int asize = getLength(buf, type);
                Object[] arr = new Object[asize];
                for ( int x = 0; x < asize; x ++ )
                    arr[x] = decode(buf);
                return arr;
            case MAP:
                int msize = getLength(buf, type);
                Map<Object,Object> map = new HashMap<Object,Object>();
                for ( int x = 0; x < msize; x ++ )
                    map.put(decode(buf), decode(buf));
                return map;
        }
        throw new DecodeException("unknown data type");
    }

    // 获得字符串
    private static String getString(ByteBuf buf, byte type) throws Exception {
        byte[] bs = getBytes(buf, type);
        try {
            return new String(bs, "utf-8");
        } catch(Exception e) {}
        return new String(bs);
    }

    // 获得byte[]
    private static byte[] getBytes(ByteBuf buf, byte type) throws Exception {
        int length = getLength(buf, type);
        byte[] res = new byte[length];
        buf.readBytes(res);
        return res;
    }

    // 获得数据的长度
    private static int getLength(ByteBuf buf, byte type) throws Exception {
        type &= 0x0f;
        if ( type <= 7 )
            return type;
        type &= 0x07;
        if ( type > 4 )
            throw new DecodeException("data length overflow");
        int length = 0;
        for ( int x = 0; x < type; x ++ )
            length |= (0xff & buf.readByte()) << (x * 8);
        if ( length < 0 )
            throw new DecodeException("invalid data length");
        return length;
    }

    /** 将Object转换为指定的数据类型（通过Gson） */
    public static <T> T toType(Object obj, Type type, Type... typeArguments) {
        Gson gson = new Gson();
        JsonElement je = gson.toJsonTree(obj);
        if ( typeArguments == null || typeArguments.length == 0 )
            return gson.fromJson(je, type);
        return gson.fromJson(je, TypeToken.getParameterized(type, typeArguments).getType());
    }

    /** 将object编码为base64格式的String */
    public static String encode(Object data) {
        ByteBuf buf = Unpooled.buffer();
        encode(buf, data);
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        buf.release();
        return Base64.getEncoder().encodeToString(bytes);
    }
    /** 将base64格式的String解码为object */
    public static Object decode(String data) throws Exception {
        byte[] bytes = Base64.getDecoder().decode(data);
        ByteBuf buf = Unpooled.wrappedBuffer(bytes);
        return decode(buf);
    }
    /** 将object编码为byte[] */
    public static byte[] encodeBytes(Object data) {
        ByteBuf buf = Unpooled.buffer();
        encode(buf, data);
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        buf.release();
        return bytes;
    }
    /** 将byte[]解码为object */
    public static Object decodeBytes(byte[] data) throws Exception {
        ByteBuf buf = Unpooled.wrappedBuffer(data);
        return decode(buf);
    }
}
