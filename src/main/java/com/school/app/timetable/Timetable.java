package com.school.app.timetable;

import com.school.app.user.Teacher;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

import java.time.DayOfWeek;
import java.util.UUID;

@Entity
@Table(name = "timetable")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Timetable {

    @Id
    @GeneratedValue
    private UUID id;

    @TenantId
    @Column(name = "school_id", nullable = false)
    private UUID schoolId;

    @Column(name = "class", nullable = false)
    private String studentClass;

    @Column(nullable = false)
    private String section;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 10)
    private DayOfWeek dayOfWeek;

    @Column(nullable = false)
    private int period;

    @Column(nullable = false)
    private String subject;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false)
    private Teacher teacher;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;
}
