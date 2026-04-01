package com.common_library.common.model.listener;

import com.common_library.common.model.BaseAuditEntity;
import jakarta.persistence.PrePersist;
import lombok.NonNull;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.data.auditing.AuditingHandler;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Configurable
public class CustomAuditingEntityListener extends AuditingEntityListener {
    public CustomAuditingEntityListener(ObjectFactory<AuditingHandler> handler){

        super.setAuditingHandler(handler);

    }

    @Override
    @PrePersist
    public void touchForCreate(Object target){
        BaseAuditEntity baseAuditEntity = (BaseAuditEntity) target;

        if(baseAuditEntity.getCreatedAt() == null){
            super.touchForCreate(target);
        }else{
            if (baseAuditEntity.getUpdatedAt() == null){
                baseAuditEntity.setUpdatedAt(baseAuditEntity.getCreatedAt());
            }
        }
    }
}
