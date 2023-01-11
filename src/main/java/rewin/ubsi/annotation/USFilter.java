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
