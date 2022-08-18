package timechannel;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import redis.clients.jedis.Jedis;

import javax.annotation.PostConstruct;
import java.io.File;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * 分配器，用于申请和续期频道的租约
 * @author antonybi
 * @date 2022/08/18
 */
@Component
@Slf4j
public class Allocator {

    private Jedis jedis;

    /**
     * lua脚本读取的byte数组
     */
    private byte[] grantScript;

    private byte[] renewScript;

    @Value("${guid.space:0}")
    private String space;

    @Value("${guid.redis.host}")
    private String redisHost;

    @Value("${guid.redis.port:6379}")
    private int redisPort;

    @PostConstruct
    public void init() {
        jedis = new Jedis(redisHost, redisPort);

        grantScript = readFile("grant.lua");
        renewScript = readFile("renew.lua");
    }

    private byte[] readFile(String filePath) {
        URL scriptUrl = this.getClass().getResource("/" + filePath);
        if (scriptUrl == null) {
            log.error("cannot find grant.lua");
            System.exit(0);
        }
        try {
            return FileUtils.readFileToByteArray(new File(scriptUrl.toURI()));
        } catch (Exception e) {
            log.error("cannot read " + filePath, e);
            System.exit(0);
            return new byte[0];
        }
    }

    /**
     * 申请一个新的租约
     * @param ttl 租约时长
     * @param appName 申请的应用名，用于日志记录
     * @return 新的租约
     */
    public Lease grant(Duration ttl, String appName) {
        Lease lease = doGrant(ttl, appName);
        log.info("grant a lease: {}", lease);
        return lease;
    }

    private Lease doGrant(Duration ttl, String appName) {
        Object result = jedis.eval(grantScript,
                0,
                space.getBytes(StandardCharsets.UTF_8),
                String.valueOf(ttl.toMillis()).getBytes(StandardCharsets.UTF_8),
                ("[" + appName + "]" + ttl).getBytes(StandardCharsets.UTF_8));
        // lua脚本自带初始化，不会出现空返回
        List<Long> response = (List<Long>) result;
        Assert.notNull(response, "redis response is null");
        if (!response.get(0).equals(1L)) {
            throw new TimeChannelInternalException("grant lease failed: " + Arrays.toString(response.toArray()));
        }

        return Lease.builder()
                .channel(response.get(1))
                .effectiveTime(response.get(2))
                .expiryTime(response.get(3))
                .build();
    }

    /**
     * 续期一个已存在的租约
     * @param lease 续期的租约
     * @param ttl 续期的时长
     */
    public void renew(Lease lease, Duration ttl) {
        Object result = jedis.eval(renewScript,
                0,
                space.getBytes(StandardCharsets.UTF_8),
                String.valueOf(lease.getChannel()).getBytes(StandardCharsets.UTF_8),
                String.valueOf(ttl.toMillis()).getBytes(StandardCharsets.UTF_8));

        List<Long> response = (List<Long>) result;
        Assert.notNull(response, "redis response is null");
        if (!response.get(0).equals(1L)) {
            throw new TimeChannelInternalException("renew lease failed: " + Arrays.toString(response.toArray()));
        }

        lease.setExpiryTime(response.get(1));
        log.info("renew lease: {}", lease);
    }

}
