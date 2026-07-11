package com.school.app.library;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookIssueRepository extends JpaRepository<BookIssue, UUID> {

    @Query("select bi from BookIssue bi join fetch bi.book join fetch bi.student "
            + "where bi.student.id = :studentId order by bi.issuedAt desc")
    List<BookIssue> findByStudentIdFetchBookAndStudent(@Param("studentId") UUID studentId);

    @Query("select bi from BookIssue bi join fetch bi.book join fetch bi.student where bi.id = :id")
    Optional<BookIssue> findByIdFetchBookAndStudent(@Param("id") UUID id);

    boolean existsByBookIdAndStudentIdAndStatus(UUID bookId, UUID studentId, BookIssueStatus status);
}
