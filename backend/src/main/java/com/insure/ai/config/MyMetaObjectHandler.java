package com.insure.ai.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.insure.ai.common.Constants;
import com.insure.ai.common.util.SecurityUtils;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 公共字段自动填充:创建者、创建时间、修改者、修改时间、逻辑删除标记
 *
 * @author insurance-ai
 */
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    private static final String FIELD_CREATE_BY = "createBy";
    private static final String FIELD_CREATE_TIME = "createTime";
    private static final String FIELD_UPDATE_BY = "updateBy";
    private static final String FIELD_UPDATE_TIME = "updateTime";
    private static final String FIELD_DELETED = "deleted";

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        String currentUser = SecurityUtils.getUsernameOrSystem();
        this.strictInsertFill(metaObject, FIELD_CREATE_BY, String.class, currentUser);
        this.strictInsertFill(metaObject, FIELD_CREATE_TIME, LocalDateTime.class, now);
        this.strictInsertFill(metaObject, FIELD_UPDATE_BY, String.class, currentUser);
        this.strictInsertFill(metaObject, FIELD_UPDATE_TIME, LocalDateTime.class, now);
        this.strictInsertFill(metaObject, FIELD_DELETED, Integer.class, Constants.NOT_DELETED);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, FIELD_UPDATE_BY, String.class, SecurityUtils.getUsernameOrSystem());
        this.strictUpdateFill(metaObject, FIELD_UPDATE_TIME, LocalDateTime.class, LocalDateTime.now());
    }
}
