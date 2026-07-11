package com.school.app.student;

import org.springframework.data.jpa.domain.Specification;

/** Builders for the optional filters on {@code GET /students} (D3: search & filters). */
final class StudentSpecifications {

    private StudentSpecifications() {
    }

    static Specification<Student> active() {
        return (root, query, cb) -> cb.isTrue(root.get("active"));
    }

    static Specification<Student> nameContains(String name) {
        String pattern = "%" + name.toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("name")), pattern);
    }

    static Specification<Student> rollNoContains(String rollNo) {
        String pattern = "%" + rollNo.toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("rollNo")), pattern);
    }

    static Specification<Student> studentClassEquals(String studentClass) {
        return (root, query, cb) -> cb.equal(cb.lower(root.get("studentClass")), studentClass.toLowerCase());
    }
}
