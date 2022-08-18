package timechannel;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.TimeZone;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * 本算法是基于snow flake的思想，实现上采用分频道+续期的思路，无额外服务仅依赖Redis。
 * <p>
 * 本实现命名为time channel算法。
 * <p>
 * 默认bit位分配：1位符号位，42位时间戳，11位频道，10位序列号。
 * 其中，时间片单位为1ms，最大时间可至2109年，频道共有2048个，序列号1024/ms（即1024000/s）。
 * 频道由allocator主动申请，由redis lua脚本进行实时分配。
 * <p>
 * 这个类是非线程安全的，调用时需加锁。如果遇到异常，外部程序需休眠一段时间重试。
 * 未做特殊说明，本程序时间单位均是ms
 *
 * @author antonybi
 * @date 2022/08/18
 */
@Component
@Slf4j
public class Generator {

    private static final Lock LOCK = new ReentrantLock();

    /**
     * 当遇到异常，均等待1ms再进行重试
     */
    private static final long ERROR_WAIT = 1;

    /**
     * 当前的租约
     */
    private Lease lease;

    /**
     * 当前时间片生成ID的计数器
     */
    private int counter;

    /**
     * 本地生效的时间，采用单调时钟System.nanoTime()
     */
    private long localEffectiveTime;

    /**
     * 上一次的时间片，用来判断是否跨时间片重新计数
     */
    private long lastFetchTime;

    /**
     * 一个时间片中最多能产生的序号数量
     */
    private int maxCount;

    /**
     * 频道的bit位数
     */
    @Value("${guid.bits.channel:11}")
    private int channelBits;

    /**
     * 序列的bit位数
     */
    @Value("${guid.bits.sequence:10}")
    private int sequenceBits;

    /**
     * 每次申请授权续期的时长，范围在1s到30m
     */
    @Value("${guid.ttl:10m}")
    private Duration ttl;

    /**
     * 续期的间隔时间
     */
    @Value("${guid.renewInterval:9m}")
    private Duration renewInterval;

    /**
     * 请求的服务名称，仅用于日志
     */
    @Value("${spring.application.name:unknown}")
    private String appName;

    @Resource
    private Allocator allocator;

    @PostConstruct
    public void init() {
        // 修正本地时间与lease时间差值
        localEffectiveTime = System.nanoTime();

        // 每个时间片最大的seq数量
        maxCount = (int) Math.pow(2, sequenceBits);

        lease = allocator.grant(ttl, appName);

        // 开启异步的续期线程
        Thread renewThread = new Thread(() -> {
            while (true) {
                try {
                    allocator.renew(lease, ttl);

                    Thread.sleep(ttl.toMillis() - renewInterval.toMillis());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    log.warn("renew lease failed, retry later", e);
                    try {
                        Thread.sleep(5000L);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }, "GUID Renew Thread");
        renewThread.start();
    }

    /**
     * 获取下一个id
     *
     * @return guid
     */
    public long nextId() {
        return next(() -> {
            // 按规则拼接bits
            long sequence = fetchSequence();
            long timeSlice = lastFetchTime;
            long guid = (timeSlice << (channelBits + sequenceBits))
                    + (lease.getChannel() << sequenceBits)
                    + sequence;

            log.debug("timeSlice: {}, sequence: {}, lease: {}, guid: {}",
                    timeSlice, sequence, lease.getChannel(), guid);
            return guid;
        });
    }

    private <T> T next(Supplier<T> supplier) {
        T nextId = null;
        LOCK.lock();
        try {
            while (nextId == null && lease != null) {
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

    private long fetchSequence() {
        // 这里需要采用nanoTime单调时钟用于计时，避免本地时钟回拨的问题
        long localServerTime = (System.nanoTime() - localEffectiveTime) / 1000000 + lease.getEffectiveTime();

        if (localServerTime < lease.getEffectiveTime() || localServerTime >= lease.getExpiryTime()) {
            throw new TimeChannelInternalException(localServerTime + " isn't in lease: " + lease);
        }

        // 时间片过期
        if (localServerTime > lastFetchTime) {
            log.debug("last time slice timeout: {}", lastFetchTime);
            lastFetchTime = localServerTime;
            counter = 0;
            return counter++;
        }

        // 时间片內序列号用尽
        if (counter >= maxCount) {
            try {
                Thread.sleep(ERROR_WAIT);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            log.debug("waiting for next time slice: {}", localServerTime);
            return fetchSequence();
        }

        return counter++;
    }

    public LocalDateTime parseDateTime(long guid) {
        return LocalDateTime.ofInstant(
                Instant.ofEpochMilli(guid >> (channelBits + sequenceBits)),
                TimeZone.getDefault().toZoneId());
    }

}
