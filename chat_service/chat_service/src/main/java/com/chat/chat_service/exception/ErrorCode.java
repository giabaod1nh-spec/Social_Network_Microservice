package com.chat.chat_service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

public enum ErrorCode {
    USER_EXISTED(1000 , "User name/email existed" , HttpStatus.BAD_REQUEST),
    USER_NOT_EXIST(1001 , "User is not existed" , HttpStatus.NOT_FOUND),
    AUTHENTICATED_FAILED(1002 , "Username or password incorrect" , HttpStatus.NOT_FOUND),
    CREATE_TOKEN_FAILED(1003 ,"Failed when create token" , HttpStatus.BAD_REQUEST),
    TOKEN_INVALID(1007, "Token invalid" , HttpStatus.BAD_REQUEST),
    TOKEN_EXPIRED(1008 , "Token expiration timed out" , HttpStatus.BAD_REQUEST),
    TOKEN_TYPE_INVALID(1009 , "Token type is not refresh" , HttpStatus.BAD_REQUEST),
    TOKEN_NOT_FOUND(1010 , "Refresh token not found in DB" , HttpStatus.BAD_REQUEST),
    TOKEN_REVOKED(1011 , "Token exists in blacklists" , HttpStatus.BAD_REQUEST),
     VERIFY_EMAIL_TOKEN_INVALID(1012 , "Email token invalid" , HttpStatus.NOT_FOUND),
    EMAIL_NOT_VERIFIED(1013 , "Email not yet verify" , HttpStatus.NOT_FOUND) ,
    UNCATEGORIZED_EXCEPTION(9999 , "Exception not yet categorized" , HttpStatus.NOT_FOUND),
    CONVERSATION_NOT_FOUND(1014, "Can not found conversation" ,HttpStatus.NOT_FOUND );

    private final int code;
    private final String message;
    private final HttpStatusCode httpStatusCode;

    ErrorCode(int code , String message , HttpStatusCode httpStatusCode){
        this.code = code ;
        this.message = message;
        this.httpStatusCode = httpStatusCode;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public HttpStatusCode getHttpStatusCode() {
        return httpStatusCode;
    }
}
