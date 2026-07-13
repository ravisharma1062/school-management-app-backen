package com.school.app.platform;

import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class RequiresEntitlementAspect {

    private final EntitlementService entitlementService;

    @Before("@annotation(requiresEntitlement)")
    public void checkEntitlement(RequiresEntitlement requiresEntitlement) {
        entitlementService.assertEntitled(requiresEntitlement.value());
    }
}
