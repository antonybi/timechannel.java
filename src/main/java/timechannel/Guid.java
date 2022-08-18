package timechannel;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 基于time channel算法生成GUID
 *
 * @author antonybi
 * @date 2022/08/18
 */
@Slf4j
@Component
public class Guid {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyMMdd");

    @Resource
    private Generator generator;

    /**
     * 生成新的guid，此方法会阻塞直到获取成功
     *
     * @return guid
     */
    public long nextId() {
        return generator.nextId();
    }

    /**
     * 可读性更好的guid，不包括前缀，固定长度不超过22个字符
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
    public LocalDateTime parseDateTime(long guid) {
        return generator.parseDateTime(guid);
    }

}
