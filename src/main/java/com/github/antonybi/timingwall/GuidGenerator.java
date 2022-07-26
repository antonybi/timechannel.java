package com.github.antonybi.timingwall;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * 基于timing wall算法生成GUID
 *
 * @author antonybi
 * @since 2021-07-15
 */
@Slf4j
@Component
public class GuidGenerator {

    private static final Lock LOCK = new ReentrantLock();

    private static final long ERROR_WAIT = 1000L;

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyMMdd");

    @Resource
    private TimingWallHandler timingWallHandler;

    /**
     * 生成新的guid，此方法会阻塞直到获取成功
     *
     * @return guid
     */
    public long nextId() {
        return next(() -> {
            long sequence = timingWallHandler.fetch();
            long guid = (timingWallHandler.getLastTimeSlice() << 25)
                    + (sequence << 14)
                    + timingWallHandler.getChannel();

            log.trace("lastTimeSlice: {}, sequence: {}, block: {}, guid: {}",
                    timingWallHandler.getLastTimeSlice(), sequence, timingWallHandler.getChannel(), guid);
            return guid;
        });
    }

    /**
     * 可读性更好的guid，固定长度且不超过25个字符
     * 由3部分组成，1：业务类型前缀， 2：日期，3：guid的十六进制字符
     *
     * @param prefix guid业务前缀
     * @return guid字符串
     */
    public String nextString(String prefix) {
        long guid = nextId();
        LocalDateTime dateTime = parseDateTime(guid);
        return String.format("%s%s%016x", prefix.toUpperCase(), dateTime.format(DATE_TIME_FORMATTER), guid);
    }

    /**
     * 解析guid的时间
     *
     * @param guid 全局id
     * @return 该guid对应的时间
     */
    public static LocalDateTime parseDateTime(long guid) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(guid >> 25 << 4), ZoneOffset.ofHours(8));
    }

    private <T> T next(Supplier<T> supplier) {
        T nextId = null;
        LOCK.lock();
        try {
            while (nextId == null) {
                try {
                    nextId = supplier.get();
                } catch (Exception e) {
                    log.warn("guid generate error", e);
                    try {
                        Thread.sleep(ERROR_WAIT);
                    } catch (InterruptedException interruptedException) {
                        log.info("interrupted", e);
                        Thread.currentThread().interrupt();
                    }
                }
            }
        } finally {
            LOCK.unlock();
        }
        return nextId;
    }

}
