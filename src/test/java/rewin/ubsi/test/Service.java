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
