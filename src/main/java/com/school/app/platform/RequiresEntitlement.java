package com.school.app.platform;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a feature entry-point method as requiring an entitlement, enforced by
 * {@link RequiresEntitlementAspect} before the method body runs. Throws
 * {@code FeatureNotEntitledException} (403 {@code FEATURE_NOT_ENTITLED}) when the current
 * tenant's plan doesn't include it.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RequiresEntitlement {

    FeatureKey value();
}
