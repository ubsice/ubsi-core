package rewin.ubsi.container;

import rewin.ubsi.common.Util;

import java.net.InetAddress;
import java.util.*;

/**
 * 微服务访问控制
 */
class ServiceAcl {
    String      Name;               // 服务名字的前缀
    byte        Policy = 0x03;      // 指定服务的整体策略
    Map<Integer, Byte> Addr = new HashMap<>();

    static int[] AcceptHost = null;         // 允许接入的主机地址
    static ServiceAcl[] AclList = null;     // 访问控制列表
    static byte         AclPolicy = 0x03;   // 所有服务的整体策略

    /* 检查连接的ACL */
    static boolean check(InetAddress remote) {
        int[] acceptHost = AcceptHost;
        if ( acceptHost == null || acceptHost.length == 0 )
            return true;
        return Arrays.binarySearch(acceptHost, Arrays.hashCode(remote.getAddress())) >= 0;
    }
    /* 检查服务的ACL */
    static boolean check(String service, Service.Entry entry, InetAddress remote) {
        byte policy = AclPolicy;
        ServiceAcl[] list = AclList;
        if ( list != null ) {
            for ( int x = 0; x < list.length; x++ ) {
                ServiceAcl acl = list[x];
                if ( service.length() == 0 ) {
                    if ( acl.Name.length() > 0 )
                        continue;
                } else if ( !Util.matchString(service, acl.Name) )
                    continue;
                Byte plc = acl.Addr.get(Arrays.hashCode(remote.getAddress()));
                policy = plc == null ? acl.Policy : plc.byteValue();
                break;
            }
        }
        if ( policy == 0x03 )
            return true;
        if ( entry.JAnnotation.readonly() )
            return (policy & 0x01) != 0;
        return (policy & 0x02) != 0;
    }

    /* 解析"rw"权限 */
    static byte parseAcl(String acl) {
        if ( acl == null || acl.trim().isEmpty() )
            return 0;
        acl = acl.toLowerCase();
        byte res = acl.indexOf('r') >= 0 ? (byte)0x01 : 0;
        res |= acl.indexOf('w') >= 0 ? (byte)0x02 : 0;
        return res;
    }

    /* 设置Acl */
    static void setAcl(Info.AclTable table) throws Exception {
        if ( table != null && table.accept_host != null ) {
            List<Integer> acceptHost = new ArrayList<>();
            for ( String host : table.accept_host ) {
                InetAddress[] addrs = InetAddress.getAllByName(host);
                for (InetAddress adr : addrs)
                    acceptHost.add(Arrays.hashCode(adr.getAddress()));
            }
            Collections.sort(acceptHost);
            int[] res = new int[acceptHost.size()];
            for ( int i = 0; i < res.length; i ++ )
                res[i] = acceptHost.get(i).intValue();
            AcceptHost = res;
        } else
            AcceptHost = null;

        byte policy = table == null || table.default_auth == null ? 0x03 : parseAcl(table.default_auth);
        ServiceAcl[] arr = null;
        if ( table != null && table.services != null ) {
            List<ServiceAcl> list = new ArrayList<>();
            for (Info.Acl acl : table.services) {
                if ( acl.name == null )
                    continue;
                ServiceAcl sacl = new ServiceAcl();
                sacl.Name = acl.name.trim();
                sacl.Policy = parseAcl(acl.default_auth);
                if ( acl.spec_auth != null ) {
                    for (String host : acl.spec_auth.keySet()) {
                        byte plc = parseAcl(acl.spec_auth.get(host));
                        InetAddress[] addrs = InetAddress.getAllByName(host);
                        for (InetAddress adr : addrs)
                            sacl.Addr.put(Arrays.hashCode(adr.getAddress()), plc);
                    }
                }
                list.add(sacl);
            }
            arr = list.toArray(new ServiceAcl[list.size()]);
            Arrays.sort(arr, (sa1, sa2) -> sa1.Name.compareTo(sa2.Name) * -1);
        }
        AclPolicy = policy;
        AclList = arr;
    }
}
