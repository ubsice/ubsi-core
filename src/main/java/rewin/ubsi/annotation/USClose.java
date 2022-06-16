package rewin.ubsi.annotation;

import java.lang.annotation.*;

/**
 * UBSI服务/Filter关闭接口的注解
 *
     @USClose
     public static void close(ServiceContext ctx) throws Exception;
     注：只能修饰public、static成员函数，且只能1个
     另外，USInit接口失败时，会自动调用USClose方法
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface USClose {
}
