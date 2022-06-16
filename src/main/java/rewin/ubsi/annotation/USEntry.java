package rewin.ubsi.annotation;

import java.lang.annotation.*;

/**
 * UBSI服务接口的注解
 *
    @USEntry(
        tips = "返回请求的数据",
        params = { @USParam(
            name = "xxx",
            tips = "xxx"
        )},
        result = "返回数据",
        readonly = true,
        timeout = 1
    )
    public Object echo(ServiceContext ctx, Object obj) throws Exception;
    注：只能修饰public、非static成员函数，可以多个
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface USEntry {
    String      tips()      default "";         // 接口说明
    USParam[]   params()    default {};         // 参数说明
    String      result()    default "";         // 结果说明
    boolean     readonly()  default true;       // 是否只读接口
    int         timeout()   default 1;          // 超时时间，秒数
}
