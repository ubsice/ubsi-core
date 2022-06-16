package rewin.ubsi.annotation;

import java.lang.annotation.*;

/**
 * UBSI服务/Filter初始化接口的注解
 *
     @USInit
     public static void init(ServiceContext ctx) throws Exception;
     注：只能修饰public、static成员函数，且只能1个
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface USInit {
}
