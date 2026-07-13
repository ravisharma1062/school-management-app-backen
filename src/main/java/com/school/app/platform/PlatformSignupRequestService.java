package com.school.app.platform;

import com.school.app.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlatformSignupRequestService {

    private final SignupRequestRepository signupRequestRepository;
    private final ProvisioningService provisioningService;

    @Transactional(readOnly = true)
    public Page<SignupRequestDto> list(Pageable pageable) {
        return signupRequestRepository.findAll(pageable).map(SignupRequestDto::from);
    }

    @Transactional(readOnly = true)
    public SignupRequestDto get(UUID id) {
        return SignupRequestDto.from(signupRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Signup request " + id + " not found")));
    }

    public ProvisionResultDto approve(UUID id, ProvisionApproveRequest request, PlatformUser actor) {
        return provisioningService.approve(id, request, actor).result();
    }

    public void reject(UUID id, PlatformUser actor) {
        provisioningService.reject(id, actor);
    }
}
