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
    String  defaultValue() default "";  // 参数缺省值（JSON格式，用在末尾的参数上，请求时如果参数数量不足，会按照缺省值自动补全）
}
