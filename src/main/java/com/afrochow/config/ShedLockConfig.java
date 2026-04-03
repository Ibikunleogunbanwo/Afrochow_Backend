package com.afrochow.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;

/**
 * ShedLock configuration — distributed scheduler locking via JDBC (MySQL).
 *
 * How it works:
 *   Before any @SchedulerLock method runs, ShedLock inserts/updates a row in
 *   the `shedlock` table (created by V15 migration). Only the instance that
 *   successfully writes that row proceeds; all others skip that cycle.
 *   The lock expires automatically after lockAtMostFor, so a crashed instance
 *   can never block the scheduler permanently.
 *
 * No Redis required — MySQL is sufficient for the scale Afrochow operates at.
 */
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class ShedLockConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        .usingDbTime()   // use DB server time — avoids clock-skew between instances
                        .build()
        );
    }
}
