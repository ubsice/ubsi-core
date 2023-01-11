/*
 * Copyright 1999-2022 Rewin Network Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package rewin.ubsi.common;

import org.bson.types.Binary;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;
import org.dom4j.tree.DefaultAttribute;
import org.dom4j.tree.DefaultElement;

import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URL;
import java.util.*;
import java.util.regex.Pattern;

/**
 * UBSI数据对象的XML编码/解码
 *      <null />
 *      <bool>true|false</bool>
 *      <byte>ff</byte>
 *      <int>123</int>
 *      <long>12345</long>
 *      <bigint>1234567</bigint>
 *      <double>12.3</double>
 *      <bignum>12.345</bignum>
 *      <bytes>a0 b1 c2</bytes>
 *      <str>hello, world</str>
 *      <list><...>...</...></list>
 *      <set><...>...</...></set>
 *      <array><...>...</...></array>
 *      <map><...>key</...><...>value</...></map>
 *      <id>...</id>
 *      <pattern flags="...">...</pattern>
 */
public class XmlCodec {

    /** 将Element解码为Object */
    public static Object decodeXml(Element el) throws Exception {
        String name = el.getName();
        switch ( name ) {
            case "null":
                return null;
            case "bool":
                return "true".equalsIgnoreCase(el.getTextTrim());
            case "byte":
                return (byte)(Integer.parseInt(el.getTextTrim(), 16) & 0xff);
            case "int":
                return Integer.parseInt(el.getTextTrim());
            case "long":
                return Long.parseLong(el.getTextTrim());
            case "bigint":
                return new BigInteger(el.getTextTrim());
            case "double":
                return Double.parseDouble(el.getTextTrim());
            case "bignum":
                return new BigDecimal(el.getTextTrim());
            case "id":
                return new ObjectId(el.getTextTrim());
            case "pattern": {
                String flags = Util.checkEmpty(el.attributeValue("flags"));
                return flags == null ? Pattern.compile(el.getTextTrim()) : Pattern.compile(el.getTextTrim(), Integer.parseInt(flags));
            }
            case "bytes":
                String[] bs = el.getTextTrim().split(" +");
                byte[] res = new byte[bs.length];
                for ( int i = 0; i < bs.length; i ++ )
                    res[i] = (byte)(Integer.parseInt(bs[i], 16) & 0xff);
                return res;
            case "str":
                return el.getStringValue();     // 自动过滤<!--...-->及识别<![CDATA[...]]>
            case "list":
                List list = new ArrayList();
                for ( Element child : el.elements() )
                    list.add(decodeXml(child));
                return list;
            case "set":
                Set set = new HashSet();
                for ( Element child : el.elements() )
                    set.add(decodeXml(child));
                return set;
            case "array":
                List arr = new ArrayList();
                for ( Element child : el.elements() )
                    arr.add(decodeXml(child));
                return arr.toArray();
            case "map":
                Map map = new HashMap();
                Object key = null;
                for ( Element child : el.elements() ) {
                    if ( key == null )
                        key = decodeXml(child);
                    else {
                        map.put(key, decodeXml(child));
                        key = null;
                    }
                }
                return map;
        }
        throw new Codec.DecodeException("unknown data type <" + name + ">");    // 不认识的数据类型
    }
    /** 将xml字符串解码为Object */
    public static Object decode(String str) throws Exception {
        Document doc = DocumentHelper.parseText(str);
        return decodeXml(doc.getRootElement());
    }
    /** 将xml文件解码为Object */
    public static Object decode(File file) throws Exception {
        SAXReader reader = new SAXReader();
        Document doc = reader.read(file);
        return decodeXml(doc.getRootElement());
    }
    /** 将URL解码为Object */
    public static Object decode(URL url) throws Exception {
        SAXReader reader = new SAXReader();
        Document doc = reader.read(url);
        return decodeXml(doc.getRootElement());
    }

    /* 将Object编码为Element，strCData表示是否将String内容放在<![CDATA[...]]>中 */
    public static Element encodeXml(Object obj, boolean strCData) throws Exception {
        Element el = null;
        if ( obj == null )
            el = new DefaultElement("null");
        else if ( obj instanceof Boolean ) {
            el = new DefaultElement("bool");
            el.setText((Boolean)obj ? "true" : "false");
        } else if ( obj instanceof Byte ) {
            el = new DefaultElement("byte");
            el.setText(Integer.toHexString(((Byte) obj).byteValue() & 0xff));
        } else if ( obj instanceof Short ) {
            el = new DefaultElement("int");
            el.setText(obj.toString());
        } else if ( obj instanceof Character ) {
            el = new DefaultElement("int");
            el.setText(Integer.toString(((Character)obj).charValue()));
        } else if ( obj instanceof Integer ) {
            el = new DefaultElement("int");
            el.setText(obj.toString());
        } else if ( obj instanceof Long ) {
            el = new DefaultElement("long");
            el.setText(obj.toString());
        } else if ( obj instanceof BigInteger ) {
            el = new DefaultElement("bigint");
            el.setText(obj.toString());
        } else if ( obj instanceof Float ) {
            el = new DefaultElement("double");
            el.setText(obj.toString());
        } else if ( obj instanceof Double ) {
            el = new DefaultElement("double");
            el.setText(obj.toString());
        } else if ( obj instanceof BigDecimal ) {
            el = new DefaultElement("bignum");
            el.setText(((BigDecimal)obj).toPlainString());
        } else if ( obj instanceof Decimal128 ) {
            el = new DefaultElement("bignum");
            el.setText(((Decimal128)obj).bigDecimalValue().toPlainString());
        } else if ( obj instanceof Throwable ) {
            el = new DefaultElement("str");
            obj = Util.getTargetThrowable((Throwable)obj);
            if ( strCData )
                el.addCDATA(obj.toString());
            else
                el.setText(obj.toString());
        } else if ( obj instanceof ObjectId ) {
            el = new DefaultElement("id");
            el.setText(((ObjectId)obj).toHexString());
        } else if ( obj instanceof Pattern ) {
            el = new DefaultElement("pattern");
            el.setAttributes(Arrays.asList(new DefaultAttribute("flags", "" + ((Pattern)obj).flags())));
            el.setText(((Pattern)obj).pattern());
        } else if ( obj instanceof byte[] || obj instanceof Binary ) {
            if ( obj instanceof Binary )
                obj = ((Binary)obj).getData();
            el = new DefaultElement("bytes");
            StringBuffer sb = new StringBuffer();
            for ( int i = 0; i < ((byte[])obj).length; i ++ ) {
                if ( i > 0 )
                    sb.append(" ");
                sb.append(Integer.toHexString(((byte[])obj)[i] & 0xff));
            }
            el.setText(sb.toString());
        } else if ( obj instanceof CharSequence ) {
            el = new DefaultElement("str");
            if ( strCData )
                el.addCDATA(obj.toString());
            else
                el.setText(obj.toString());
        } else if ( obj.getClass().isArray() ) {
            el = new DefaultElement("array");
            int length = Array.getLength(obj);
            for ( int i = 0; i < length; i ++ )
                el.add(encodeXml(Array.get(obj, i), strCData));
        } else if ( obj instanceof List ) {
            el = new DefaultElement("list");
            int length = ((List)obj).size();
            for ( int i = 0; i < length; i ++ )
                el.add(encodeXml(((List)obj).get(i), strCData));
        } else if ( obj instanceof Set ) {
            el = new DefaultElement("set");
            for ( Object o : (Set)obj )
                el.add(encodeXml(o, strCData));
        } else if ( obj instanceof Map ) {
            el = new DefaultElement("map");
            for ( Object k : ((Map)obj).keySet() ) {
                el.add(encodeXml(k, strCData));
                el.add(encodeXml(((Map)obj).get(k), strCData));
            }
        } else {
            Map<String,Object> map = Codec.obj2Map(obj);    // 按Value-Object对象处理
            el = encodeXml(map, strCData);   // 转换为Map进行编码
        }
        return el;
    }
    /** 将Object编码为xml字符串，strCData表示是否将String内容放在<![CDATA[...]]>中，filterHeader表示是否过滤<?xml version="1.0" encoding="UTF-8"?> */
    public static String encode(Object obj, boolean strCData, boolean filterHeader) throws Exception {
        Document doc = DocumentHelper.createDocument();
        doc.add(encodeXml(obj, strCData));
        String xml = doc.asXML();
        if ( !filterHeader )
            return xml;
        int i1 = xml.indexOf('<');
        int i2 = xml.indexOf("<?");
        if ( i1 < 0 || i2 < 0 || i1 != i2 )
            return xml;
        i2 = xml.indexOf("?>");
        if ( i2 < 0 )
            return xml;
        i1 = xml.indexOf('<', i2 + 2);
        return xml.substring(i1 > 0 ? i1 : i2 + 2);
    }
    /** 将Object写入xml文件，strCData表示是否将String内容放在<![CDATA[...]]>中 */
    public static void encode(Object obj, boolean strCData, File file) throws Exception {
        Document doc = DocumentHelper.createDocument();
        doc.add(encodeXml(obj, strCData));
        OutputFormat format = OutputFormat.createPrettyPrint();
        format.setEncoding("utf-8");
        FileWriter writer = null;
        XMLWriter xmlWriter = null;
        try {
            writer = new FileWriter(file);
            xmlWriter = new XMLWriter(writer, format);
            xmlWriter.write(doc);
        } finally {
            if ( xmlWriter != null )
                xmlWriter.close();
            if ( writer != null )
                writer.close();
        }
    }
}
