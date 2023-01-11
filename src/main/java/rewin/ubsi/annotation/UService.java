/*
 * Copyright 1999-2022 Rewin Network Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
