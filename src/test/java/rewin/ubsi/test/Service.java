package rewin.ubsi.test;

import rewin.ubsi.annotation.*;
import rewin.ubsi.container.ServiceContext;

import java.util.concurrent.atomic.AtomicInteger;

@UService(
        name = Service.NAME,
        tips = "测试微服务",
        version = "1.0.0"
)
public class Service {
    final static String NAME = "ubsi.test";

    static AtomicInteger totalCount = new AtomicInteger(0); // 总调用次数

    int thisCount = totalCount.intValue();

    @USBefore
    public void before(ServiceContext ctx) throws Exception {
        ctx.getLogger().info("---before---", thisCount);
    }
    @USAfter
    public void after(ServiceContext ctx) throws Exception {
        ctx.getLogger().info("---after---", thisCount);
    }

    @USEntry(
            tips = "返回请求的数据",
            params = {@USParam(name="obj", tips="请求的数据")},
            result = "请求数据"
    )
    public Object echo(ServiceContext ctx, Object obj) throws Exception {
        ctx.getLogger().info("---invoke---", thisCount);
        totalCount.incrementAndGet();
        return obj;
    }
}
