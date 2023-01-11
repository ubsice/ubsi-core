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

package rewin.ubsi.cli;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;
import rewin.ubsi.common.JsonCodec;
import rewin.ubsi.common.Util;
import rewin.ubsi.common.XmlCodec;
import rewin.ubsi.consumer.Context;
import rewin.ubsi.container.Bootstrap;

import java.io.File;
import java.io.StringWriter;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 命令行工具：发送请求
 */
public class Request {
    /** XML格式输出 */
    public static void printXml(Object data) throws Exception {
        Document doc = DocumentHelper.createDocument();
        doc.add(XmlCodec.encodeXml(data, false));
        OutputFormat format = OutputFormat.createPrettyPrint();
        format.setSuppressDeclaration(true);
        StringWriter out = new StringWriter();
        XMLWriter writer = new XMLWriter(out, format);
        writer.write(doc);
        writer.close();
        System.out.println(out.toString());
    }

    /** JSON格式输出 */
    public static void printJson(Object data) {
        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        System.out.println(gson.toJson(data));
    }

    /** 从xml生成Consumer请求对象 */
    public static Context fromXml(Document doc) throws Exception {
        Element root = doc.getRootElement();
        String service = root.attributeValue("service", "").trim();
        String entry = root.attributeValue("entry", "").trim();
        String timeout = root.attributeValue("timeout", "").trim();

        if ( entry.isEmpty() )
            return null;        // 没有entry

        int min = 0;
        int max = 0;
        int rel = -1;
        Map<String, Object> header = new HashMap<>();
        List<Object> params = new ArrayList<>();
        params.add(entry);
        for ( Element el : root.elements() ) {
            switch ( el.getName() ) {
                case "version":
                    String vmin = el.attributeValue("min", "").trim();
                    String vmax = el.attributeValue("max", "").trim();
                    String vrel = el.attributeValue("release", "").trim();
                    if ( !vmin.isEmpty() )
                        min = Integer.parseInt(vmin);
                    if ( !vmax.isEmpty() )
                        max = Integer.parseInt(vmax);
                    if ( !vrel.isEmpty() )
                        rel = Integer.parseInt(vrel);
                    break;
                case "header":
                    for ( Element head : el.elements() )
                        header.put(head.getName(), XmlCodec.decodeXml(head.elements().get(0)));
                    break;
                case "params":
                    for ( Element param : el.elements() )
                        params.add(XmlCodec.decodeXml(param));
                    break;
            }
        }

        Context context = Context.request(service, params.toArray());
        if ( !timeout.isEmpty() )
            context.setTimeout(Integer.parseInt(timeout));
        context.setVersion(min, max, rel);
        context.setHeader(header);
        return context;
    }

    /** xml data format */
    public final static String XML_FORMAT = "" +
            "\t\t\t<null/>\n" +
            "\t\t\t<bool>true|false</bool>\n" +
            "\t\t\t<byte>ff</byte>\n" +
            "\t\t\t<int>1234</int>\n" +
            "\t\t\t<long>12345678</long>\n" +
            "\t\t\t<bigint>1234567890</bigint>\n" +
            "\t\t\t<double>1.23</double>\n" +
            "\t\t\t<bignum>1.23456</bignum>\n" +
            "\t\t\t<bytes>a0 b1 c2 ...</bytes>\n" +
            "\t\t\t<str>hello, world</str>\n" +
            "\t\t\t<str><![CDATA[hello, world]]></str>\n" +
            "\t\t\t<array><TYPE>VALUE1</TYPE>...</array>\n" +
            "\t\t\t<list><TYPE>VALUE1</TYPE>...</list>\n" +
            "\t\t\t<set><TYPE>VALUE1</TYPE>...</set>\n" +
            "\t\t\t<map><TYPE>KEY1</TYPE><TYPE>VALUE1</TYPE>...</map>\n" +
            "\t\t\t<id>...</id>\n" +
            "\t\t\t<pattern flags=\"2\">^H.*T$</pattern> <!-- 0:大小写敏感, 2:大小写不敏感 -->";
    /** xml request syntax */
    public final static String XML_REQUEST = "" +
            "\t<ubsi service=\"SERVICE_NAME\" entry=\"ENTRY_NAME\" timeout=\"SECONDS\">\n" +
            "\t\t<version min=\"1.0.0\" max=\"1.1.0\" release=\"-1|0|1\"/>\n" +
            "\t\t<header>\n" +
            "\t\t\t<KEY1><TYPE>VALUE1</TYPE></KEY1>\n" +
            "\t\t</header>\n" +
            "\t\t<params>\n" +
            XML_FORMAT +
            "\t\t</params>\n" +
            "\t</ubsi>";

    /** json data format */
    public final static String JSON_FORMAT = "" +
            "\t\t\tnull -> null\n" +
            "\t\t\tbool -> true|false\n" +
            "\t\t\tbyte -> { \"$t\": \"byte\", \"$v\": -1 }\n" +
            "\t\t\tint -> 123\n" +
            "\t\t\tlong -> { \"$t\": \"long\", \"$v\": 12345 }\n" +
            "\t\t\tbigint -> { \"$t\": \"bigint\", \"$v\": \"1234567\" }\n" +
            "\t\t\tdouble -> 123.45 or { \"$t\": \"double\", \"$v\": 123 } (当值为整数时的表示方式)\n" +
            "\t\t\tbignum -> { \"$t\": \"bignum\", \"$v\": \"12345.67\" }\n" +
            "\t\t\tbytes -> { \"$t\": \"bytes\", \"$v\": \"b0a1\" }\n" +
            "\t\t\tstr -> \"hello, world\"\n" +
            "\t\t\tarray -> { \"$t\": \"array\", \"$v\": [ ... ] }\n" +
            "\t\t\tlist -> [ ... ]\n" +
            "\t\t\tset -> { \"$t\": \"set\", \"$v\": [ ... ] }\n" +
            "\t\t\tmap -> { ... }\n" +
            "\t\t\tid -> { \"$t\": \"id\", \"$v\": \"xxxxxx\" }\n" +
            "\t\t\tpattern -> { \"$t\": \"pattern\", \"$v\": \"^H.*T$/2\" } // 0:大小写敏感, 2:大小写不敏感\n";
    /** json request syntax */
    public final static String JSON_REQUEST = "" +
            "\t{\n" +
            "\t\t\"service\": \"SERVICE_NAME\",\n" +
            "\t\t\"entry\": \"ENTRY_NAME\",\n" +
            "\t\t\"timeout\": 0,\n" +
            "\t\t\"version\": {\n" +
            "\t\t\t\"min\": \"0.0.0\",\n" +
            "\t\t\t\"max\": \"0.0.0\",\n" +
            "\t\t\t\"rel\": -1\n" +
            "\t\t},\n" +
            "\t\t\"header\": {\n" +
            "\t\t},\n" +
            "\t\t\"params\": [\n" +
            JSON_FORMAT +
            "\t\t]\n" +
            "\t}";

    public static class Version {
        public String   min = "0.0.0";
        public String   max = "0.0.0";
        public int      rel = -1;
    }
    public static class JReq {
        public String   service;
        public String   entry;
        public int      timeout;
        public Version  version;
        public Map      header;
        public List     params;
    }

    /** 从json生成Consumer请求对象 */
    public static Context fromJson(String filename) throws Exception {
        JReq req = Util.readJsonFile(new File(filename), JReq.class);
        req.params = (List)JsonCodec.decodeType(req.params);
        if ( req.params == null )
            req.params = new ArrayList();
        req.params.add(0, req.entry);
        Context context = Context.request(req.service, req.params.toArray());
        if ( req.timeout >= 0 )
            context.setTimeout(req.timeout);
        if ( req.header != null )
            context.setHeader(req.header);
        if ( req.version != null ) {
            int min = Util.getVersion(req.version.min);
            int max = Util.getVersion(req.version.max);
            context.setVersion(min, max, req.version.rel);
        }
        return context;
    }

    /** 主程序入口 */
    public static void main(String[] args) throws Exception {
        String host = "localhost";
        int port = Bootstrap.DEFAULT_PORT;
        String file = null;
        String service = null;
        List params = new ArrayList();
        boolean log = false;
        boolean router = false;
        boolean xml = false;
        for ( int i = 0; i < args.length; i ++ ) {
            if ( "-h".equals(args[i]) ) {
                host = args[i+1];
                i ++;
            } else if ( "-p".equals(args[i]) ) {
                port = Integer.parseInt(args[i+1]);
                i ++;
            } else if ( "-f".equals(args[i]) ) {
                file = args[i+1];
                i ++;
            } else if ( "-router".equals(args[i]) )
                router = true;
            else if ( "-log".equals(args[i]) )
                log = true;
            else if ( "-xml".equals(args[i]) )
                xml = true;
            else if ( service == null )
                service = args[i];
            else if ( params.isEmpty() )
                params.add(args[i]);
            else if ( xml )
                params.add(XmlCodec.decode(args[i]));
            else
                params.add(JsonCodec.fromJson(args[i]));
        }

        if ( file == null && (service == null || params.isEmpty()) ) {
            System.out.println("\nUsage: Request [-h host] [-p port] [-f req-file] [-router] [-log] [-xml] service entry ...\n");
            if ( xml ) {
                System.out.println("req-file format(xml):");
                System.out.println("\t<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
                System.out.println(XML_REQUEST);
            } else {
                System.out.println("req-file format(json):");
                System.out.println(JSON_REQUEST);
                System.out.println("!! use ' instead of \" if parameters in command line");
            }
            return;
        }

        Context.setLogApp(InetAddress.getLocalHost().getHostName(), "rewin.ubsi.cli.Request");
        Context.startup(".");
        try {
            Context context = null;
            if ( file != null ) {
                if ( xml )
                    context = fromXml(new SAXReader().read(new File(file)));
                else
                    context = fromJson(file);
            } else
                context = Context.request(service, params.toArray());

            if ( log )
                context.setLogAccess(true);     // 打开访问日志

            Object res = router ? context.call() : context.direct(host, port);
            if (xml)
                printXml(res);
            else
                printJson(JsonCodec.encodeType(res));
        } catch (Exception e) {
            e.printStackTrace();
        }
        Context.shutdown();
    }
}
