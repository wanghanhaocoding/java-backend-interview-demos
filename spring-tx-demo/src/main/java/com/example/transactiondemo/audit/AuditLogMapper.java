package com.example.transactiondemo.audit;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AuditLogMapper {

    @Insert("insert into audit_log(message) values(#{message})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AuditLog auditLog);

    @Select("select count(*) from audit_log")
    long count();
}
