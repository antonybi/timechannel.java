package com.github.antonybi.timingwall;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * @author antony
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = {GuidGenerator.class})
@TestPropertySource("classpath:application-test.yml")
@ContextConfiguration(classes = {TestConfig.class})
@ActiveProfiles("test")
@Slf4j
class GuidGeneratorTest {

    @Resource
    private GuidGenerator guidGenerator;

    @SneakyThrows
    @Test
    void nextId() {
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
    }

    private void consumeGuid(Set<Long> rst, CountDownLatch c) {
        for (int i = 0; i < 300000; i++) {
            long id = guidGenerator.nextId();
            assertFalse(rst.contains(id));
            rst.add(id);
        }
        c.countDown();
    }

    @Test
    void nextString() {
    }

    @Test
    void parseDateTime() {
    }
}