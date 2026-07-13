package com.school.app.common.config;

import com.school.app.common.security.TenantRlsTransactionListener;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;

/**
 * Registers {@link TenantRlsTransactionListener} onto the transaction manager. Kept out of
 * {@link HibernateTenancyConfig}: this class depends on {@link PlatformTransactionManager}, which
 * itself depends on the {@code EntityManagerFactory} — bean creation for the EMF is already
 * underway by the time {@code PlatformTransactionManager} exists, so nothing that needs it may
 * also (even transitively) be a source of the {@code HibernatePropertiesCustomizer} beans the EMF
 * creation collects, or Spring reports an unresolvable circular reference.
 */
@Configuration
@RequiredArgsConstructor
public class RlsTransactionListenerConfig {

    private final PlatformTransactionManager transactionManager;
    private final TenantRlsTransactionListener tenantRlsTransactionListener;

    @PostConstruct
    void registerTenantRlsListener() {
        if (transactionManager instanceof AbstractPlatformTransactionManager abstractTransactionManager) {
            abstractTransactionManager.addListener(tenantRlsTransactionListener);
        }
    }
}
