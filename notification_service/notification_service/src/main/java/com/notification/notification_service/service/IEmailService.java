package com.notification.notification_service.service;

import java.io.IOException;

public interface IEmailService {
    void emailVerfication(String userEmail , String userName , String token) throws IOException;
}
