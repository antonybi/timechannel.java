package timechannel.core;

/**
 * 频道的租约
 * @author antonybi
 * @since 2022/08/18
 */
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
     * 过期时间
     */
    private long expiryTime;

    long getChannel() {
        return channel;
    }

    void setChannel(long channel) {
        this.channel = channel;
    }

    long getEffectiveTime() {
        return effectiveTime;
    }

    void setEffectiveTime(long effectiveTime) {
        this.effectiveTime = effectiveTime;
    }

    long getExpiryTime() {
        return expiryTime;
    }

    void setExpiryTime(long expiryTime) {
        this.expiryTime = expiryTime;
    }

    @Override
    public String toString() {
        return "Lease{" +
                "channel=" + channel +
                ", effectiveTime=" + effectiveTime +
                ", expiryTime=" + expiryTime +
                '}';
    }

}
