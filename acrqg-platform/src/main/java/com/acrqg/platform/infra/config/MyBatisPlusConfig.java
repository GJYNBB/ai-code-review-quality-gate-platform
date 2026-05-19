package com.acrqg.platform.infra.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import java.time.LocalDateTime;
import org.apache.ibatis.reflection.MetaObject;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 全局配置。
 *
 * <p>装配三件事：
 * <ol>
 *   <li>Mapper 扫描根：{@code com.acrqg.platform.**.repository}，与 design.md §4.3
 *       的包内分层规范保持一致（每个业务模块的 MyBatis Mapper 接口位于自身的
 *       {@code repository} 子包下）；</li>
 *   <li>{@link MybatisPlusInterceptor}：
 *       <ul>
 *         <li>{@link PaginationInnerInterceptor}：方言 {@link DbType#POSTGRE_SQL}，
 *             {@code maxLimit=1000} 防止单页过大拖垮 DB；</li>
 *         <li>{@link OptimisticLockerInnerInterceptor}：在带 {@code @Version}
 *             字段的实体上启用乐观锁，配合
 *             {@code ReviewTask}（design §7.2 含 {@code version}）等需要并发安全
 *             的状态机迁移；</li>
 *       </ul>
 *       两个 inner interceptor 注册顺序无强约束，但 design 一致采用"分页在前、乐观锁
 *       在后"以便分页插件先改写 SQL；</li>
 *   <li>{@link AuditMetaObjectHandler}：在 INSERT / UPDATE 时自动填充
 *       {@code createdAt} / {@code updatedAt}（仅当实体上有对应字段且声明了
 *       {@code @TableField(fill=...)} 时生效）。这避免每个 Service 都手写
 *       时间戳赋值，且与 PostgreSQL 端 {@code updated_at} 触发器互为兜底
 *       （Java 端先填，DB 触发器作为最后防线）。</li>
 * </ol>
 *
 * <p>{@link AuditMetaObjectHandler} 内联为 {@code static class} 以避免文件膨胀；
 * 其行为为"幂等填充"：若实体没有 {@code createdAt} / {@code updatedAt} 字段，
 * 通过 {@link MetaObject#hasGetter(String)} 判定后跳过，不抛异常。
 *
 * <p>Covers: R3.4 / R4.5 / R22.1（审计时间戳与不可变字段填充）。
 *
 * @see com.acrqg.platform.infra.config.RedisConfig
 */
@Configuration
@MapperScan("com.acrqg.platform.**.repository")
public class MyBatisPlusConfig {

    /** 单页最大条数，防御性约束以避免误用 pageSize=Integer.MAX_VALUE 拖垮 DB。 */
    static final long MAX_PAGE_LIMIT = 1000L;

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        PaginationInnerInterceptor paginationInner = new PaginationInnerInterceptor(DbType.POSTGRE_SQL);
        paginationInner.setMaxLimit(MAX_PAGE_LIMIT);
        // 当 pageSize 超过 maxLimit 时按 maxLimit 截断而非抛异常，便于上层稳定处理
        paginationInner.setOverflow(false);
        interceptor.addInnerInterceptor(paginationInner);

        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());

        return interceptor;
    }

    @Bean
    public MetaObjectHandler auditMetaObjectHandler() {
        return new AuditMetaObjectHandler();
    }

    /**
     * 审计时间戳字段填充。
     *
     * <p>约定字段名：{@code createdAt}（实体属性）/ {@code created_at}（DB 列）；
     * {@code updatedAt} / {@code updated_at} 同理。实体上需要 {@code @TableField(fill=...)}
     * 才会触发该 handler；未声明的实体不会被改写。
     */
    static final class AuditMetaObjectHandler implements MetaObjectHandler {

        private static final String CREATED_AT = "createdAt";
        private static final String UPDATED_AT = "updatedAt";

        @Override
        public void insertFill(MetaObject metaObject) {
            LocalDateTime now = LocalDateTime.now();
            if (metaObject.hasGetter(CREATED_AT)) {
                strictInsertFill(metaObject, CREATED_AT, LocalDateTime.class, now);
            }
            if (metaObject.hasGetter(UPDATED_AT)) {
                strictInsertFill(metaObject, UPDATED_AT, LocalDateTime.class, now);
            }
        }

        @Override
        public void updateFill(MetaObject metaObject) {
            if (metaObject.hasGetter(UPDATED_AT)) {
                strictUpdateFill(metaObject, UPDATED_AT, LocalDateTime.class, LocalDateTime.now());
            }
        }
    }
}
