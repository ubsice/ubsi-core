package rewin.ubsi.annotation;

import java.lang.annotation.*;

/**
 * UBSI服务接口参数的注解，只能用在@USEntry注解中
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface USParam {
    String  name()      default "";     // 参数名字
    String  tips()      default "";     // 参数说明
}
