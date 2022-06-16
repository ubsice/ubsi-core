package rewin.ubsi.annotation;

import java.lang.annotation.*;

/**
 * 微服务接口/Filter的前置注解
 *
    @USBefore
    public void before(ServiceContext ctx) throws Exception;
    注：只能修饰public、非static成员函数，且只能1个
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface USBefore {
    int timeout()   default 1;          // 超时时间，秒数
}
