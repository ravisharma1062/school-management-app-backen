package com.school.app.platform;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.annotation.Annotation;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RequiresEntitlementAspectTest {

    @Mock
    private EntitlementService entitlementService;

    @InjectMocks
    private RequiresEntitlementAspect aspect;

    private RequiresEntitlement annotationFor(FeatureKey key) {
        return new RequiresEntitlement() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return RequiresEntitlement.class;
            }

            @Override
            public FeatureKey value() {
                return key;
            }
        };
    }

    @Test
    void delegatesToEntitlementServiceForTheAnnotatedFeature() {
        aspect.checkEntitlement(annotationFor(FeatureKey.MESSAGING));

        verify(entitlementService).assertEntitled(FeatureKey.MESSAGING);
    }

    @Test
    void propagatesTheExceptionEntitlementServiceThrows() {
        doThrow(new com.school.app.common.exception.FeatureNotEntitledException("not entitled"))
                .when(entitlementService).assertEntitled(FeatureKey.TRANSPORT_TRACKING);

        org.junit.jupiter.api.Assertions.assertThrows(
                com.school.app.common.exception.FeatureNotEntitledException.class,
                () -> aspect.checkEntitlement(annotationFor(FeatureKey.TRANSPORT_TRACKING)));
    }
}
