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
