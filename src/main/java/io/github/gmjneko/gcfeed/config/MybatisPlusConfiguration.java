package io.github.gmjneko.gcfeed.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.VendorDatabaseIdProvider;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.mybatis.spring.annotation.MapperScan;

import java.time.LocalDateTime;
import java.util.Properties;

/**
 * MyBatis-Plus 配置。
 *
 * <p>承担三件事：
 * <ol>
 *   <li>扫描 Mapper 接口，避免每个 Mapper 都写 {@code @Mapper}。</li>
 *   <li>注册分页插件与全表更新/删除防护插件。</li>
 *   <li>统一自动填充 {@code createdAt} / {@code updatedAt}。</li>
 * </ol>
 */
@Configuration(proxyBeanMethods = false)
@MapperScan("io.github.gmjneko.gcfeed.modules.**.mapper")
public class MybatisPlusConfiguration {

    /**
     * MyBatis-Plus 插件链。
     *
     * <p>插件顺序有要求：分页插件必须在最后注册，否则多插件组合时可能失效。
     * {@link BlockAttackInnerInterceptor} 会拦截没有 where 条件的 update/delete，
     * 属于低成本高收益的保护，防止误操作清表。
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    /**
     * 数据库厂商标识，供 {@code databaseId} 分支选择方言相关的 SQL 写法。
     *
     * <p>目前只有向量 upsert 用到：MySQL 8.0.20 起 {@code VALUES(col)} 废弃、要改用
     * {@code AS new} 别名形式，而 H2 的 MySQL 兼容模式只认旧写法。测试跑 H2、生产跑 MySQL，
     * 两边都需要能通过，所以按 {@code databaseId} 在 Mapper 里二选一。
     */
    @Bean
    public DatabaseIdProvider databaseIdProvider() {
        VendorDatabaseIdProvider provider = new VendorDatabaseIdProvider();
        Properties properties = new Properties();
        properties.setProperty("MySQL", "mysql");
        properties.setProperty("H2", "h2");
        provider.setProperties(properties);
        return provider;
    }

    /**
     * 公共字段自动填充。
     *
     * <p>插入时填充 {@code createdAt}（若未显式赋值）与 {@code updatedAt}；
     * 更新时只刷新 {@code updatedAt}。这样业务代码不必每次手写时间。
     */
    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {

            @Override
            public void insertFill(MetaObject metaObject) {
                LocalDateTime now = LocalDateTime.now();
                strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
                strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
            }

        };
    }

}
