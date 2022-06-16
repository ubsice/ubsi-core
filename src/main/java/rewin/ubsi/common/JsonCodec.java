package rewin.ubsi.common;

import com.google.gson.*;
import org.bouncycastle.util.encoders.Hex;
import org.bson.types.Binary;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.regex.Pattern;

/**
 * UBSI数据对象的XML编码/解码
 *                  null
 *      boolean     true|false
 *      byte        { DT: "byte", DV: -1 }
 *      int         123
 *      long        { DT: "long", DV: 12345 }
 *      BigInteger  { DT: "bigint", DV: "1234567" }
 *      double      123.45 or { DT: "double", DV: 123 }
 *      BigDecimal  { DT: "bignum", DV: "12.345" }
 *      byte[]      { DT: "bytes", DV: "a0b1c2" }
 *      String      "hello, world"
 *      Map         { ... }
 *      List        [ ... ]
 *      Set         { DT: "set", DV: [ ... ] }
 *      Object[]    { DT: "array", DV: [ ... ] }
 *      ObjectId    { DT: "id", DV: "xxxx" }
 *      Pattern     { DT: "pattern", DV: "xxxx/x" }
 */
public class JsonCodec {

    public static final String DT = "$t";
    public static final String DV = "$v";

    public static final String T_BYTE = "byte";
    public static final String T_LONG = "long";
    public static final String T_BIGINT = "bigint";
    public static final String T_DOUBLE = "double";
    public static final String T_BIGNUM = "bignum";
    public static final String T_BYTES = "bytes";
    public static final String T_ARRAY = "array";
    public static final String T_SET = "set";
    public static final String T_ID = "id";
    public static final String T_PATTERN = "pattern";

    /** 将JsonElement解码为Object */
    public static Object fromJson(JsonElement je) throws Exception {
        if ( je.isJsonNull() )
            return null;
        if ( je.isJsonPrimitive() ) {
            JsonPrimitive jp = je.getAsJsonPrimitive();
            if ( jp.isString() )
                return jp.getAsString();
            if ( jp.isBoolean() )
                return jp.getAsBoolean();
            if ( jp.isNumber() ) {
                double d = jp.getAsDouble();
                int i = (int)d;
                if ( d != (double)i )
                    return d;
                return i;
            }
            throw new Codec.DecodeException("unknown json primitive: " + jp.toString());
        }
        if ( je.isJsonArray() ) {
            JsonArray ja = je.getAsJsonArray();
            List list = new ArrayList();
            for ( int i = 0; i < ja.size(); i ++ )
                list.add(fromJson(ja.get(i)));
            return list;
        }
        if ( je.isJsonObject() ) {
            JsonObject jo = je.getAsJsonObject();
            if ( !jo.has(DT) || !jo.has(DV) ) {
                Map map = new HashMap();
                for ( String k : jo.keySet() )
                    map.put(k, fromJson(jo.get(k)));
                return map;
            }
            String dt = jo.get(DT).getAsString();
            switch ( dt ) {
                case T_BYTE:
                    return jo.get(DV).getAsByte();
                case T_LONG:
                    return jo.get(DV).getAsLong();
                case T_BIGINT:
                    return jo.get(DV).getAsBigInteger();
                case T_DOUBLE:
                    return jo.get(DV).getAsDouble();
                case T_BIGNUM:
                    return jo.get(DV).getAsBigDecimal();
                case T_BYTES:
                    return Hex.decode(jo.get(DV).getAsString());
                case T_ARRAY: {
                    JsonArray ja = jo.get(DV).getAsJsonArray();
                    Object[] oa = new Object[ja.size()];
                    for ( int i = 0; i < oa.length; i ++ )
                        oa[i] = fromJson(ja.get(i));
                    return oa;
                }
                case T_SET: {
                    JsonArray ja = jo.get(DV).getAsJsonArray();
                    Set set = new HashSet();
                    for ( int i = 0; i < ja.size(); i ++ )
                        set.add(fromJson(ja.get(i)));
                    return set;
                }
                case T_ID:
                    return new ObjectId(jo.get(DV).getAsString());
                case T_PATTERN:
                    String pf = jo.get(DV).getAsString();
                    int index = pf.lastIndexOf('/');
                    if ( index < 0 || index == pf.length() - 1 )
                        return Pattern.compile(pf);
                    int flags = Integer.parseInt(pf.substring(index+1));
                    return Pattern.compile(pf.substring(0, index), flags);
            }
            throw new Codec.DecodeException("unknown json type: " + dt);
        }
        throw new Codec.DecodeException("unknown json data: " + je.toString());
    }

    /** 将json字符串解码为Object */
    public static Object fromJson(String str) throws Exception {
        JsonElement je = JsonParser.parseString(str);
        return fromJson(je);
    }

    // 创建一个JsonObject
    static JsonObject toJsonObject(String t, JsonElement v) throws Exception {
        JsonObject jo = new JsonObject();
        jo.addProperty(DT, t);
        jo.add(DV, v);
        return jo;
    }
    /** 将Object编码为JsonElement */
    public static JsonElement toJson(Object obj) throws Exception {
        if ( obj == null )
            return JsonNull.INSTANCE;
        if ( obj instanceof Boolean )
            return new JsonPrimitive((Boolean)obj);
        if ( obj instanceof Byte )
            return toJsonObject(T_BYTE, new JsonPrimitive((Byte)obj));
        if ( obj instanceof Short )
            return new JsonPrimitive((Short)obj);
        if ( obj instanceof Character )
            return new JsonPrimitive((int)((Character)obj));
        if ( obj instanceof Integer )
            return new JsonPrimitive((Integer)obj);
        if ( obj instanceof Long )
            return toJsonObject(T_LONG, new JsonPrimitive((Long)obj));
        if ( obj instanceof BigInteger)
            return toJsonObject(T_BIGINT, new JsonPrimitive(obj.toString()));
        if ( obj instanceof Float || obj instanceof Double ) {
            double d = obj instanceof Float ? ((Float)obj).doubleValue() : (Double)obj;
            int i = (int)d;
            if ( d == (double)i )
                return toJsonObject(T_DOUBLE, new JsonPrimitive(i));
            return new JsonPrimitive(d);
        }
        if ( obj instanceof BigDecimal || obj instanceof Decimal128 ) {
            if (obj instanceof Decimal128)
                obj = ((Decimal128) obj).bigDecimalValue();
            return toJsonObject(T_BIGNUM, new JsonPrimitive(((BigDecimal) obj).toPlainString()));
        }
        if ( obj instanceof Throwable )
            return new JsonPrimitive(Util.getTargetThrowable((Throwable)obj).toString());
        if ( obj instanceof ObjectId )
            return toJsonObject(T_ID, new JsonPrimitive(((ObjectId)obj).toHexString()));
        if ( obj instanceof Pattern )
            return toJsonObject(T_PATTERN, new JsonPrimitive(((Pattern)obj).pattern() + "/" + ((Pattern)obj).flags()));
        if ( obj instanceof byte[] || obj instanceof Binary) {
            if ( obj instanceof Binary )
                obj = ((Binary)obj).getData();
            return toJsonObject(T_BYTES, new JsonPrimitive(Hex.toHexString((byte[])obj)));
        }
        if ( obj instanceof CharSequence )
            return new JsonPrimitive(obj.toString());
        if ( obj.getClass().isArray() ) {
            JsonArray ja = new JsonArray();
            int length = Array.getLength(obj);
            for ( int i = 0; i < length; i ++ )
                ja.add(toJson(Array.get(obj, i)));
            return toJsonObject(T_ARRAY, ja);
        }
        if ( obj instanceof List ) {
            JsonArray ja = new JsonArray();
            int length = ((List)obj).size();
            for ( int i = 0; i < length; i ++ )
                ja.add(toJson(((List)obj).get(i)));
            return ja;
        }
        if ( obj instanceof Set ) {
            JsonArray ja = new JsonArray();
            for ( Object o : (Set)obj )
                ja.add(toJson(o));
            return toJsonObject(T_SET, ja);
        }
        if ( obj instanceof Map ) {
            JsonObject jo = new JsonObject();
            for ( Object k : ((Map)obj).keySet() )
                jo.add(k.toString(), toJson(((Map)obj).get(k)));
            return jo;
        }
        // Java对象，转换为Map进行处理
        Map<String,Object> map = Codec.obj2Map(obj);
        return toJson(map);
    }

    /** 数据编码：JSON数据类型 */
    public static Object encodeType(Object obj) {
        if ( obj == null ||
                obj instanceof Boolean ||
                obj instanceof Short ||
                obj instanceof Integer ||
                obj instanceof CharSequence )
            return obj;

        if ( obj instanceof Byte )
            return Util.toMap(DT, T_BYTE, DV, obj);
        if ( obj instanceof Character )
            return (int)((Character)obj);
        if ( obj instanceof Long )
            return Util.toMap(DT, T_LONG, DV, obj);
        if ( obj instanceof BigInteger)
            return Util.toMap(DT, T_BIGINT, DV, obj.toString());
        if ( obj instanceof Float || obj instanceof Double ) {
            double d = obj instanceof Float ? ((Float)obj).doubleValue() : (Double)obj;
            int i = (int)d;
            if ( d == (double)i )
                return Util.toMap(DT, T_DOUBLE, DV, obj);
            return obj;
        }
        if ( obj instanceof BigDecimal || obj instanceof Decimal128 ) {
            if (obj instanceof Decimal128)
                obj = ((Decimal128) obj).bigDecimalValue();
            return Util.toMap(DT, T_BIGNUM, DV, ((BigDecimal)obj).toPlainString());
        }
        if ( obj instanceof Throwable )
            return Util.getTargetThrowable((Throwable)obj).toString();
        if ( obj instanceof ObjectId )
            return Util.toMap(DT, T_ID, DV, ((ObjectId) obj).toHexString());
        if ( obj instanceof Pattern )
            return Util.toMap(DT, T_PATTERN, DV, ((Pattern)obj).pattern() + "/" + ((Pattern)obj).flags());
        if ( obj instanceof byte[] || obj instanceof Binary) {
            if ( obj instanceof Binary )
                obj = ((Binary)obj).getData();
            return Util.toMap(DT, T_BYTES, DV, Hex.toHexString((byte[])obj));
        }
        if ( obj.getClass().isArray() ) {
            List list = new ArrayList();
            int length = Array.getLength(obj);
            for ( int i = 0; i < length; i ++ )
                list.add(encodeType(Array.get(obj, i)));
            return Util.toMap(DT, T_ARRAY, DV, list);
        }
        if ( obj instanceof List ) {
            List list = new ArrayList();
            int length = ((List)obj).size();
            for ( int i = 0; i < length; i ++ )
                list.add(encodeType(((List)obj).get(i)));
            return list;
        }
        if ( obj instanceof Set ) {
            List list = new ArrayList();
            for ( Object o : (Set)obj )
                list.add(encodeType(o));
            return Util.toMap(DT, T_SET, DV, list);
        }
        if ( obj instanceof Map ) {
            Map map = new HashMap();
            for ( Object k : ((Map)obj).keySet() )
                map.put(k, encodeType(((Map)obj).get(k)));
            return map;
        }
        // Java对象，转换为Map进行处理
        Map<String,Object> map = Codec.obj2Map(obj);
        return encodeType(map);
    }

    /** 数据解码：JSON数据类型 */
    public static Object decodeType(Object obj) throws Exception {
        if ( obj == null )
            return null;
        if ( obj instanceof Number ) {
            double d = ((Number)obj).doubleValue();
            int i = (int)d;
            if ( d != (double)i )
                return obj;
            return i;
        }
        if ( obj instanceof Map ) {
            Map map = new HashMap();
            if ( !map.containsKey(DT) || !map.containsKey(DV) ) {
                for ( Object k : ((Map)obj).keySet() )
                    map.put(k, decodeType(((Map)obj).get(k)));
                return map;
            }
            String dt = (String)map.get(DT);
            Object dv = map.get(DV);
            switch ( dt ) {
                case T_BYTE:
                    return ((Number)dv).byteValue();
                case T_LONG:
                    return ((Number)dv).longValue();
                case T_BIGINT:
                    return new BigInteger((String)dv);
                case T_DOUBLE:
                    return ((Number)dv).doubleValue();
                case T_BIGNUM:
                    return new BigDecimal((String)dv);
                case T_BYTES:
                    return Hex.decode((String)dv);
                case T_ARRAY: {
                    List list = (List)dv;
                    Object[] oa = new Object[list.size()];
                    for ( int i = 0; i < oa.length; i ++ )
                        oa[i] = decodeType(list.get(i));
                    return oa;
                }
                case T_SET: {
                    List list = (List)dv;
                    Set set = new HashSet();
                    for ( int i = 0; i < list.size(); i ++ )
                        set.add(decodeType(list.get(i)));
                    return set;
                }
                case T_ID:
                    return new ObjectId((String)dv);
                case T_PATTERN:
                    String pf = (String)dv;
                    int index = pf.lastIndexOf('/');
                    if ( index < 0 || index == pf.length() - 1 )
                        return Pattern.compile(pf);
                    int flags = Integer.parseInt(pf.substring(index+1));
                    return Pattern.compile(pf.substring(0, index), flags);
            }
            throw new Codec.DecodeException("unknown json type: " + dt);
        }
        if ( obj.getClass().isArray() ) {
            int length = Array.getLength(obj);
            Object[] oa = new Object[length];
            for ( int i = 0; i < length; i ++ )
                oa[i] = decodeType(Array.get(obj, i));
            return oa;
        }
        if ( obj instanceof List ) {
            List list = new ArrayList();
            int length = ((List)obj).size();
            for ( int i = 0; i < length; i ++ )
                list.add(decodeType(((List)obj).get(i)));
            return list;
        }
        if ( obj instanceof Set ) {
            Set set = new HashSet();
            for ( Object o : (Set)obj )
                set.add(decodeType(o));
            return set;
        }
        return obj;
    }

    // 解析正常的JSON数据
    static Object jsonValue(JsonElement je) throws Exception {
        if ( je.isJsonNull() )
            return null;
        if ( je.isJsonPrimitive() ) {
            JsonPrimitive jp = je.getAsJsonPrimitive();
            if ( jp.isString() )
                return jp.getAsString();
            if ( jp.isBoolean() )
                return jp.getAsBoolean();
            if ( jp.isNumber() ) {
                double d = jp.getAsDouble();
                int i = (int)d;
                if ( d != (double)i )
                    return d;
                return i;
            }
            throw new Exception("unknown json primitive: " + jp.toString());
        }
        if ( je.isJsonArray() ) {
            JsonArray ja = je.getAsJsonArray();
            List list = new ArrayList();
            for ( int i = 0; i < ja.size(); i ++ )
                list.add(jsonValue(ja.get(i)));
            return list;
        }
        if ( je.isJsonObject() ) {
            JsonObject jo = je.getAsJsonObject();
            Map map = new HashMap();
            for (String k : jo.keySet())
                map.put(k, jsonValue(jo.get(k)));
            return map;
        }
        throw new Exception("unknown json data: " + je.toString());
    }
    /** 解析正常的JSON字符串 */
    public static Object simpleJson(String json) throws Exception {
        JsonElement je = JsonParser.parseString(json);
        return jsonValue(je);
    }

}
