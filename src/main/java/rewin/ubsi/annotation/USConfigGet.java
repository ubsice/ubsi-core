package rewin.ubsi.annotation;

import java.lang.annotation.*;

/**
 * UBSI服务/Filter配置读取接口的注解
 *
     @USConfig
     public static Object getConfig(ServiceContext ctx) throws Exception;
     注：只能修饰public、static成员函数，且只能1个
     另外，本接口应该能在微服务未"启动"状态下执行，以便支持远程部署和配置
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface USConfigGet {
}
