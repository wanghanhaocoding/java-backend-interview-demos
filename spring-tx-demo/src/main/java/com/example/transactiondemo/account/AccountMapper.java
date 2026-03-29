package com.example.transactiondemo.account;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AccountMapper {

    @Select("select id, owner, balance from account where id = #{id}")
    Account findById(@Param("id") Long id);

    @Update("update account set balance = #{balance} where id = #{id}")
    int updateBalance(@Param("id") Long id, @Param("balance") java.math.BigDecimal balance);
}
