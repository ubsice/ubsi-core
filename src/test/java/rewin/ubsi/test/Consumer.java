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
