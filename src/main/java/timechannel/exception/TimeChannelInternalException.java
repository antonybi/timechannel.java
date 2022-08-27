package timechannel.exception;


/**
 * 处理异常，此类问题正常不应该会发生
 *
 * @author antonybi
 * @since 2022/08/18
 **/
public class TimeChannelInternalException extends RuntimeException {

    private final String message;

    public TimeChannelInternalException(String message) {
        super(message);
        this.message = message;
    }

    @Override
    public String getMessage() {
        return message;
    }

}
