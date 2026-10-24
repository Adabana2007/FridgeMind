package com.fridgemind.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

/** MyBatis-Plus 插件配置：注册分页插件（MySQL）与自动填充 */
@Configuration
public class MyBatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        // 分页插件：MySQL 物理分页，Page 参数会注入到 SQL 的 LIMIT；如需乐观锁/自动填充可继续 addInnerInterceptor(...)
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    /**
     * updated_at 自动填充：updateById(实体) 会把实体里的旧值一并写进 SET，
     * 压过数据库的 ON UPDATE CURRENT_TIMESTAMP，只有在这里统一赋值才能保证每处更新都刷新时间。
     */
    @Bean
    public MetaObjectHandler updatedAtFillHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                setFieldValByName("updatedAt", LocalDateTime.now(), metaObject);
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                setFieldValByName("updatedAt", LocalDateTime.now(), metaObject);
            }
        };
    }
}
