package com.notification.notification_service.repository;

import com.notification.notification_service.entity.UserDevices;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserDeviceRepository extends MongoRepository<UserDevices , String> {

    List<UserDevices> findAllByUserId(String userId);
}
