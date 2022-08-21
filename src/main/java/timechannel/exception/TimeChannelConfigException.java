package timechannel.exception;


import lombok.Getter;

/**
 * 配置错误
 *
 * @author antonybi
 * @date 2022/08/21
 **/
@Getter
public class TimeChannelConfigException extends RuntimeException {

    private final String message;

    public TimeChannelConfigException(String message) {
        super(message);
        this.message = message;
    }

    public TimeChannelConfigException(String message, Throwable cause) {
        super(message, cause);
        this.message = message;
    }
}
