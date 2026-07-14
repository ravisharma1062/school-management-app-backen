package com.school.app.platform;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SignupRequestRepository extends JpaRepository<SignupRequest, UUID> {

    Optional<SignupRequest> findByContactEmailAndStatus(String contactEmail, SignupRequestStatus status);
}
