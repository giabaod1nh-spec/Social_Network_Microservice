package com.profile_service.profile.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    USER_EXISTED(3000 , "User name/email existed" , HttpStatus.BAD_REQUEST),
    AUTHENTICATED_FAILED(3001 , "Username or password incorrect" , HttpStatus.NOT_FOUND),
    PROFILE_NOT_FOUND(3002 , "Profile not exists" , HttpStatus.NOT_FOUND),
    ALREADY_FOLLOWED(3003 , "You already followed this user" , HttpStatus.FOUND),
    ERROR_WHEN_FOLLOW(3004 , "Some error happened when tryin follow" , HttpStatus.BAD_REQUEST),
    NOT_FOLLOWED(3003 , "You not followed this user" , HttpStatus.FOUND),
    CANT_FOLLOW_YOURSELF(3004 , "Cant be followed yourself", HttpStatus.FOUND)

    ;

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;
     ErrorCode(int code , String message , HttpStatus httpStatus){
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
