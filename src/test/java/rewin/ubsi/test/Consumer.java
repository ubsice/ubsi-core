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

package rewin.ubsi.test;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import rewin.ubsi.consumer.Context;
import rewin.ubsi.container.Bootstrap;

public class Consumer {
    @Before
    public void before() throws Exception {
        Bootstrap.start();
    }

    @After
    public void after() throws Exception {
        Bootstrap.stop();
    }

    @Test
    public void test() throws Exception {
        for ( int i = 0; i < 3; i ++ ) {
            Context context = Context.request(Service.NAME,"echo", "hello, ubsi");
            Object res = context.call();
            Context.getLogger("ubsi.tester", "rewin.ubsi.test.Consumer").info("test-result", res);
        }
    }

}
