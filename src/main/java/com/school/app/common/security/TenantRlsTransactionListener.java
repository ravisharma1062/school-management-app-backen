package com.school.app.common.security;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.Session;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionExecution;
import org.springframework.transaction.TransactionExecutionListener;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.PreparedStatement;
import java.util.UUID;

/**
 * Sets the Postgres session variable the row-level-security policies (V17 migration) check,
 * at the start of every transaction. Registered onto the JPA transaction manager by
 * {@link com.school.app.common.config.RlsTransactionListenerConfig}.
 *
 * <p>Must run the {@code SELECT set_config(...)} on the SAME physical connection Hibernate will
 * use for the rest of this transaction, or the policies check a variable set on a connection that
 * never runs the actual queries. {@code JpaTransactionManager} binds its resource under the
 * {@code EntityManagerFactory} key (not the raw {@code DataSource}), so that's the key to look it
 * up by — {@code Session.doWork(...)} then hands us that exact connection.
 *
 * <p>Uses {@code SELECT set_config(..., true)} rather than {@code SET LOCAL} directly, because
 * plain {@code SET} statements don't accept a JDBC bind parameter; {@code set_config}'s third
 * argument ({@code is_local = true}) gives the same transaction-scoped, auto-reset-on-commit
 * behaviour as {@code SET LOCAL} — which matters because the underlying connection is pooled
 * (HikariCP) and gets reused by later, differently-tenanted transactions.
 */
@Component
public class TenantRlsTransactionListener implements TransactionExecutionListener {

    private final EntityManagerFactory entityManagerFactory;

    public TenantRlsTransactionListener(EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
    }

    @Override
    public void afterBegin(TransactionExecution transactionExecution, Throwable beginFailure) {
        if (beginFailure != null || !TenantContext.isSet()) {
            return;
        }
        Object resource = TransactionSynchronizationManager.getResource(entityManagerFactory);
        if (!(resource instanceof EntityManagerHolder holder)) {
            return;
        }
        applyCurrentTenant(holder.getEntityManager());
    }

    /**
     * Re-applies {@link TenantContext}'s current value to the RLS session variable on demand,
     * mid-transaction. Needed by callers that only learn the tenant partway through an already-
     * open {@code @Transactional} method — {@link #afterBegin} alone isn't enough there, since it
     * only runs once, before the method body (and therefore before that discovery) executes. See
     * {@code PaymentService.handleWebhook} for the motivating case (a gateway webhook carries no
     * JWT, so the tenant is discovered from the payment row itself, inside the transaction).
     */
    public void applyCurrentTenant(EntityManager entityManager) {
        if (!TenantContext.isSet()) {
            return;
        }
        UUID schoolId = TenantContext.get();
        Session session = entityManager.unwrap(Session.class);
        session.doWork(connection -> {
            try (PreparedStatement ps = connection.prepareStatement("SELECT set_config('app.current_school_id', ?, true)")) {
                ps.setString(1, schoolId.toString());
                ps.execute();
            }
        });
    }
}
