package com.github.antonybi.timingbrick;


import lombok.Getter;

/**
 * 处理异常，此类问题不应该会发生
 *
 * @author antonybi
 * @since 2021-07-15
 **/
@Getter
public class TimingBrickException extends RuntimeException {

    private final String message;

    public TimingBrickException(String message) {
        super(message);
        this.message = message;
    }

    public TimingBrickException(String message, Throwable cause) {
        super(message, cause);
        this.message = message;
    }
}
