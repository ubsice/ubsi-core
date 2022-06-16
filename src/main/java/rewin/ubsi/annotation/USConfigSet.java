package rewin.ubsi.annotation;

import java.lang.annotation.*;

/**
 * UBSI服务/Filter配置更改接口的注解
 *
    @USConfigSet
    public static void setConfig(ServiceContext ctx, String json) throws Exception;
    注：只能修饰public、static成员函数，且只能1个；参数为JSON格式的配置项信息
    另外，本接口应该能在微服务未"启动"状态下执行，以便支持远程部署和配置
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface USConfigSet {
}
