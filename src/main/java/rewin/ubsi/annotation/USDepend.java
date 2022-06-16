package rewin.ubsi.annotation;

import java.lang.annotation.*;

/**
 * UBSI服务/Filter依赖的注解，只能用在@UService/@USFilter注解中
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface USDepend {
    String  name()      default "";     // 服务名字
    String  version()   default "";     // 最小版本
    boolean release()   default false;  // 是否必须为正式版本
}
