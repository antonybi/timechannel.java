package timechannel.core;

import lombok.*;

/**
 * 频道的租约
 * @author antonybi
 * @since 2022/08/18
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class Lease {

    /**
     * 频道
     */
    private long channel;

    /**
     * 生效时间
     */
    private long effectiveTime;

    /**
     * 过期时间（注：这里虽然涉及多线程操作，但是逻辑上既做了提前更新，且含义上读取到旧值不会有任何问题，故没使用并发类）
     */
    private long expiryTime;

}
