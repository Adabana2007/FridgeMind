package com.fridgemind.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fridgemind.entity.Admin;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AdminMapper extends BaseMapper<Admin> {

    default Admin selectByUsername(String username) {
        return selectOne(new LambdaQueryWrapper<Admin>()
                .eq(Admin::getUsername, username));
    }
}
