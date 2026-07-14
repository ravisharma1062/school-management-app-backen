package com.school.app.platform;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * MT-6f — global, singleton (one row, seeded by {@code V23}) platform-wide settings. Global like
 * {@code PlatformUser}/{@code SignupRequest} (no {@code @TenantId}) since these aren't per-tenant.
 */
@Entity
@Table(name = "platform_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatformSettings {

    /** The one row this table will ever have — seeded by {@code V23}. */
    public static final UUID SINGLETON_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Id
    private UUID id;

    /**
     * When true, {@code PublicSignupService} auto-provisions new signup requests immediately via
     * the same {@code ProvisioningService.approve} path an operator would otherwise click through
     * — the "graduation of MT-3's provisioning service into a self-service trigger" the plan
     * describes. Defaults to false ("keep vetting for paid conversion" until volume justifies it).
     */
    @Column(name = "auto_approve_signups", nullable = false)
    private boolean autoApproveSignups;

    /** MT-5 (manual billing) — shown to every school on its billing page: bank details for NEFT, cheque/DD payee, etc. */
    @Column(name = "payment_instructions", columnDefinition = "TEXT")
    private String paymentInstructions;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void onSave() {
        updatedAt = Instant.now();
    }
}
