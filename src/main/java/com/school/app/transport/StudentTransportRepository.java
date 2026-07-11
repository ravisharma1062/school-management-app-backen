package com.school.app.transport;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface StudentTransportRepository extends JpaRepository<StudentTransport, UUID> {

    @Query("select st from StudentTransport st "
            + "join fetch st.route join fetch st.stop where st.student.id = :studentId")
    Optional<StudentTransport> findByStudentIdFetchRouteAndStop(@Param("studentId") UUID studentId);

    Optional<StudentTransport> findByStudentId(UUID studentId);
}
