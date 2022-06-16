package rewin.ubsi.annotation;

import java.lang.annotation.*;

/**
 * UBSI服务中用来注释"数据结构"的注解
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface USNote {
    String  value()      default "";     // 注释
}
