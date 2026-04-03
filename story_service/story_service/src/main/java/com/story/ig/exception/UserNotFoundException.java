package com.story.ig.exception;

public class UserNotFoundException extends RuntimeException{
    public UserNotFoundException(String userId){
        super("User with userId: : "+ userId + "doesn't existed");
    }
}
