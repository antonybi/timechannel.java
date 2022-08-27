package timechannel.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import timechannel.Guid;
import timechannel.TestConfig;
import timechannel.exception.TimeChannelInternalException;

import javax.annotation.Resource;

import java.time.Duration;
import java.time.temporal.TemporalUnit;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author antonybi
 * @since 2022/08/28
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = {Guid.class})
@ContextConfiguration(classes = {TestConfig.class})
@ActiveProfiles("test")
class AllocatorTest {

    @Resource
    private Allocator allocator;

    @Test
    void grant() {
        for (int i = 0; i < 256 - 1; i++) {
            // 本应用启动已经占用一个
            allocator.grant(256, Duration.ofSeconds(15), "junit");
        }
        assertThrowsExactly(TimeChannelInternalException.class,
                () -> allocator.grant(256, Duration.ofSeconds(15), "junit"));
    }

}