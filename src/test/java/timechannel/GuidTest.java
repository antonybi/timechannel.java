package timechannel;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author antonybi
 * @since 2022/08/18
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = {Guid.class})
@ContextConfiguration(classes = {TestConfig.class})
@ActiveProfiles("test")
class GuidTest {

    private final Logger log = LoggerFactory.getLogger(GuidTest.class);

    @Resource
    private Guid guid;

    @Disabled("调整sequence的配置，12bit可达到50w/s")
    @Test
    void benchmarkNextId() throws InterruptedException {
        CountDownLatch c = new CountDownLatch(3);

        Thread t1 = new Thread(() -> {
            consumeGuid(c);
        });
        Thread t2 = new Thread(() -> {
            consumeGuid(c);
        });
        Thread t3 = new Thread(() -> {
            consumeGuid(c);
        });
        t1.start();
        t2.start();
        t3.start();

        c.await(5, TimeUnit.MINUTES);
        log.info("done!");
    }

    private void consumeGuid(CountDownLatch c) {
        for (int i = 0; i < 10000000; i++) {
            assertDoesNotThrow(() -> guid.nextId());
        }
        c.countDown();
    }

    @Test
    void nextId() {
        Set<Long> rst = Collections.synchronizedSet(new HashSet<>());
        long last = 0;

        for (int i = 0; i < 100; i++) {
            // 加速消耗直到一个时间片內序号都耗尽
            long id = guid.nextId();
            assertTrue(id > last);
            assertFalse(rst.contains(id));
            rst.add(id);
            last = id;
        }
    }

    @Test
    void nextId2() {
        assertEquals(4 + 22, guid.nextId("TEST").length());
    }

    @Test
    void parseDateTime() {
        assertEquals(LocalDateTime.parse("2022-08-28T00:22:33.251"), guid.parseDateTime(13611969357836288L));
    }

}