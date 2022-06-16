package rewin.ubsi.annotation;

import java.lang.annotation.*;

/**
 * UBSI服务注解
 *
    @UService(
        name = "缺省的服务名字",
        tips = "UBSI微服务",
        version = "1.0.0",
        release = false,
        depend = { @USDepend (
            name = "xxx",
            version = "",
            release = false
        ) }
    )
    注：服务类必须有缺省的或无参数的构造函数
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface UService {
    String  name()      default "";         // 缺省的服务名字
    String  tips()      default "";
    String  version()   default "0.0.1";    // 最多3段，每段最多0~999
    boolean release()   default false;
    USDepend[] depend() default {};         // 依赖的其他服务
    // since 1.0.1
    String  container() default "";         // 依赖的容器的版本号
    String[] syslib()   default {};         // 需要加载到SystemClassLoader中的Jar包，例如"jaxen-1.2.0.jar"
    // since 2.0.0
    boolean singleton() default false;      // 是否单例服务
}
