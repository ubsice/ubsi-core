package rewin.ubsi.common;

import com.mongodb.*;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * MongoDB客户端工具
 *      支持MongoDB 3.x以上，依赖mongo-java-driver:3.10.1
 *      支持单机/群集/分片模式
 *      不支持SSL连接模式
 */
public class MongoUtil {

    public static String CONFIG_FILE = "rewin.ubsi.mongodb.json";

    /** 服务器地址 */
    public static class Server {
        public String   host = "localhost";     // 主机名
        public int      port = 27017;           // 端口号
    }
    /** 服务器地址说明 */
    public static String ServerComment = "MongoDB服务器地址列表：[{\"host\":\"xxx\",\"port\":27017}]";
    /** 用户认证 */
    public static class Auth {
        public String   username;       // 用户名
        public String   password;       // 密码，可以为null或""
        public String   database;       // Database名
    }
    /** 用户认证说明 */
    public static String AuthComment = "MongoDB数据库用户列表：[{\"username\":\"xxx\",\"password\":\"xxx\",\"database\":\"xxx\"}]";
    /** 配置项 */
    public static class Option {
        public Integer  connections_per_host;       // 每个主机的最大连接数
        public Integer  min_connections_per_host;   // 每个主机的最小连接数
        public Integer  connect_timeout;            // 连接超时时间（毫秒）
        public Integer  max_wait_time;              // 线程阻塞等待连接的最长时间（毫秒）
        public Integer  read_preference;            // 读节点，0:nearest, 1:primary, 2:primaryPreferred, -1:secondary, -2:secondaryPreferred
        public Integer  server_selection_timeout;   // 服务器选择超时（毫秒）
        public Boolean  retry_writes;               // 是否重试写操作
        public Integer  socket_timeout;             // socket超时时间（毫秒）
        //过期3.12.10 public Integer  threads_multiplier;         // 等待连接的阻塞线程数量（连接数的倍数）
        public String   required_replica_set_name;  // 集群所需的副本集名称
        public Integer  max_connection_idle_time;   // 连接池的最大空闲时间
        public Integer  max_connection_life_time;   // 连接池的最大生命时间
    }
    /** 配置项说明 */
    public static class OptionComment {
        public String   connections_per_host =      "每个主机的最大连接数";
        public String   min_connections_per_host =  "每个主机的最小连接数";
        public String   connect_timeout =           "连接超时时间（毫秒）";
        public String   max_wait_time =             "线程阻塞等待连接的最长时间（毫秒）";
        public String   read_preference =           "读节点，0:nearest, 1:primary, 2:primaryPreferred, -1:secondary, -2:secondaryPreferred";
        public String   server_selection_timeout =  "服务器选择超时（毫秒）";
        public String   retry_writes =              "是否重试写操作";
        public String   socket_timeout =            "socket超时时间（毫秒）";
        //过期3.12.10 public String   threads_multiplier =        "等待连接的阻塞线程数量（连接数的倍数）";
        public String   required_replica_set_name = "集群所需的副本集名称";
        public String   max_connection_idle_time =  "连接池的最大空闲时间";
        public String   max_connection_life_time =  "连接池的最大生命时间";
    }
    /** 配置信息 */
    @SuppressWarnings("unchecked")
    public static class Config {
        public List<Server> servers = Util.toList(new Server());    // 服务器列表
        public List<Auth>   auth;       // 用户认证
        public Option       option;     // 配置项
    }

    /** 检查配置参数 */
    @SuppressWarnings("unchecked")
    public static void checkConfig(Config config) {
        if (config.servers == null )
            config.servers = Util.toList(new Server());
        else if ( config.servers.isEmpty() )
            config.servers.add(new Server());
    }

    /** 创建MongoClient实例 */
    public static MongoClient getMongoClient(Config config, String dbname) {
        if ( config == null )
            config = new Config();
        else
            checkConfig(config);

        List<ServerAddress> servers = new ArrayList<>();
        for ( Server server : config.servers ) {
            ServerAddress addr = new ServerAddress(server.host, server.port);
            servers.add(addr);
        }

        MongoCredential credential = null;
        if ( config.auth != null ) {
            for ( Auth auth : config.auth ) {
                if ( auth.username != null && auth.password != null && auth.database != null && auth.database.equals(dbname) ) {
                    credential = MongoCredential.createCredential(auth.username, auth.database, auth.password.toCharArray());
                    break;
                }
            }
        }

        MongoClientOptions.Builder ops = MongoClientOptions.builder();
        CodecRegistry pojo = CodecRegistries.fromRegistries(MongoClientSettings.getDefaultCodecRegistry(),
                CodecRegistries.fromProviders(PojoCodecProvider.builder().automatic(true).build()));
        ops.codecRegistry(pojo);        // 设置POJO的编码解码器
        if ( config.option != null ) {
            if ( config.option.connections_per_host != null )
                ops.connectionsPerHost(config.option.connections_per_host);
            if ( config.option.min_connections_per_host != null )
                ops.minConnectionsPerHost(config.option.min_connections_per_host);
            if ( config.option.connect_timeout != null )
                ops.connectTimeout(config.option.connect_timeout);
            if ( config.option.max_wait_time != null )
                ops.maxWaitTime(config.option.max_wait_time);
            if ( config.option.read_preference != null ) {
                if ( config.option.read_preference == 1 )
                    ops.readPreference(ReadPreference.primary());
                else if ( config.option.read_preference > 1 )
                    ops.readPreference(ReadPreference.primaryPreferred());
                else if ( config.option.read_preference == -1 )
                    ops.readPreference(ReadPreference.secondary());
                else if ( config.option.read_preference < -1 )
                    ops.readPreference(ReadPreference.secondaryPreferred());
                else
                    ops.readPreference(ReadPreference.nearest());
            }
            if ( config.option.server_selection_timeout != null )
                ops.serverSelectionTimeout(config.option.server_selection_timeout);
            if ( config.option.retry_writes != null )
                ops.retryWrites(config.option.retry_writes);
            if ( config.option.socket_timeout != null )
                ops.socketTimeout(config.option.socket_timeout);
            /*过期3.12.10
            if ( config.option.threads_multiplier != null )
                ops.threadsAllowedToBlockForConnectionMultiplier(config.option.threads_multiplier);
             */
            if ( config.option.required_replica_set_name != null )
                ops.requiredReplicaSetName(config.option.required_replica_set_name);
            if ( config.option.max_connection_idle_time != null )
                ops.maxConnectionIdleTime(config.option.max_connection_idle_time);
            if ( config.option.max_connection_life_time != null )
                ops.maxConnectionLifeTime(config.option.max_connection_life_time);
        }

        if ( servers.size() == 1 ) {
            if ( credential == null )
                return new MongoClient(servers.get(0), ops.build());
            return new MongoClient(servers.get(0), credential, ops.build());
        }

        if ( credential == null )
            return new MongoClient(servers, ops.build());
        return new MongoClient(servers, credential, ops.build());
    }

    /** 创建Collection及索引，参数的格式：[ "collection_name", List<Bson>, ... ] */
    @SuppressWarnings("unchecked")
    public static void createCollectionAndIndex(MongoDatabase db, Object[] list) {
        List<String> cols = db.listCollectionNames().into(new ArrayList<>());
        for (int i = 0; i < list.length; i += 2) {
            if (cols.contains((String)list[i]))
                continue;
            db.createCollection((String)list[i]);
            if ( i < list.length - 1 && list[i+1] != null ) {
                MongoCollection<Document> col = db.getCollection((String) list[i]);
                for (Bson index : (List<Bson>) list[i + 1])
                    col.createIndex(index);
            }
        }
    }

    /** MongoDB通用查询 */
    public static <T> List<T> query(MongoCollection<T> col, Bson filter, Bson sort, int skip, int limit, Bson fields) {
        FindIterable<T> iter = filter == null ? col.find() : col.find(filter);
        if ( sort != null )
            iter.sort(sort);
        if ( skip > 0 )
            iter.skip(skip);
        if ( limit > 0 )
            iter.limit(limit);
        if ( fields != null )
            iter.projection(fields);
        return iter.into(new ArrayList<>());
    }

}
