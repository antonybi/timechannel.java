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

    @Disabled("very slow")
    @Test
    void benchmarkNextId() throws InterruptedException {
        Set<Long> rst = Collections.synchronizedSet(new HashSet<>());
        CountDownLatch c = new CountDownLatch(3);

        Thread t1 = new Thread(() -> {
            consumeGuid(rst, c);
        });
        Thread t2 = new Thread(() -> {
            consumeGuid(rst, c);
        });
        Thread t3 = new Thread(() -> {
            consumeGuid(rst, c);
        });
        t1.start();
        t2.start();
        t3.start();

        c.await(5, TimeUnit.MINUTES);
        log.info("done!");
    }

    private void consumeGuid(Set<Long> rst, CountDownLatch c) {
        for (int i = 0; i < 10000000; i++) {
            long id = guid.nextId();
            assertFalse(rst.contains(id));
            rst.add(id);
        }
        c.countDown();
    }

    @Test
    void nextId() {
        assertTrue(guid.nextId() > 0);
        assertTrue(guid.nextId() > 0);
    }

    @Test
    void nextId2() {
        assertEquals(4 + 22, guid.nextId("TEST").length());
    }

    @Test
    void parseDateTime() {
        assertEquals(LocalDateTime.parse("2075-04-23T11:50:00.727"), guid.parseDateTime(3484645589754769408L));
    }

}