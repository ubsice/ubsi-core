package rewin.ubsi.annotation;

import java.lang.annotation.*;

/**
 * UBSI容器过滤器注解
 *
    @USFilter(
        tips = "微服务容器过滤器",
        version = "1.0.0",
        release = false,
        depend = { @USDepend (
            name = "xxx",
            version = "",
            release = false
        ) }
    注：Filter类必须有缺省的或无参数的构造函数
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface USFilter {
    String  tips()      default "";
    String  version()   default "0.0.1";    // 最多3段，每段最多0~999
    boolean release()   default false;
    USDepend[] depend() default {};         // 依赖的其他服务
    // since 1.0.1
    String  container() default "";         // 依赖的容器的版本号
    String[] syslib()   default {};         // 需要加载到SystemClassLoader中的Jar包，例如"jaxen-1.2.0.jar"
}
