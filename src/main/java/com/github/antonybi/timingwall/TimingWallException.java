package com.github.antonybi.timingwall;


import lombok.Getter;

/**
 * 处理异常，此类问题不应该会发生
 *
 * @author antonybi
 * @since 2021-07-15
 **/
@Getter
public class TimingWallException extends RuntimeException {

    private final String message;

    public TimingWallException(String message) {
        super(message);
        this.message = message;
    }

    public TimingWallException(String message, Throwable cause) {
        super(message, cause);
        this.message = message;
    }
}
