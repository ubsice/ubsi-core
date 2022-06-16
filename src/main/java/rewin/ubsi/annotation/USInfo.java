package rewin.ubsi.annotation;

import java.lang.annotation.*;

/**
 * UBSI服务/Filter运行信息查询接口的注解
 *
     @USInfo
     public static Object info(ServiceContext ctx) throws Exception;
     注：只能修饰public、static成员函数，且只能1个
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface USInfo {
}
