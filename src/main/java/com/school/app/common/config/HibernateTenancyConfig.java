package com.school.app.common.config;

import com.school.app.common.security.SchoolTenantResolver;
import lombok.RequiredArgsConstructor;
import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Deliberately depends on nothing but {@link SchoolTenantResolver} (itself dependency-free).
 * {@link HibernatePropertiesCustomizer} beans are collected while Spring Boot builds the
 * {@code EntityManagerFactory} — a config class providing one must not, even transitively via
 * constructor injection, depend on anything that itself needs the {@code EntityManagerFactory}
 * (e.g. {@code PlatformTransactionManager}), or bean creation becomes circular. See
 * {@link RlsTransactionListenerConfig} for the piece that does need the transaction manager.
 */
@Configuration
@RequiredArgsConstructor
public class HibernateTenancyConfig {

    private final SchoolTenantResolver schoolTenantResolver;

    @Bean
    public HibernatePropertiesCustomizer tenantIdentifierResolverCustomizer() {
        return properties -> properties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, schoolTenantResolver);
    }
}
