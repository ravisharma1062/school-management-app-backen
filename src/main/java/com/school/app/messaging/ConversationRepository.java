package com.school.app.messaging;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    @Query("SELECT c FROM Conversation c JOIN FETCH c.parent JOIN FETCH c.teacher WHERE c.id = :id")
    Optional<Conversation> findByIdFetchParticipants(@Param("id") UUID id);

    @Query("SELECT c FROM Conversation c JOIN FETCH c.parent JOIN FETCH c.teacher "
            + "WHERE c.parent.id = :parentId AND c.teacher.id = :teacherId")
    Optional<Conversation> findByParentIdAndTeacherIdFetchParticipants(
            @Param("parentId") UUID parentId, @Param("teacherId") UUID teacherId);

    @Query("SELECT c FROM Conversation c JOIN FETCH c.parent JOIN FETCH c.teacher "
            + "WHERE c.parent.id = :parentId ORDER BY c.createdAt DESC")
    List<Conversation> findByParentIdFetchParticipants(@Param("parentId") UUID parentId);

    @Query("SELECT c FROM Conversation c JOIN FETCH c.parent JOIN FETCH c.teacher "
            + "WHERE c.teacher.id = :teacherId ORDER BY c.createdAt DESC")
    List<Conversation> findByTeacherIdFetchParticipants(@Param("teacherId") UUID teacherId);
}
