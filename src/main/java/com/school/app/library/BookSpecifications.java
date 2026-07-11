package com.school.app.library;

import org.springframework.data.jpa.domain.Specification;

/** Builder for the optional catalog search filter on {@code GET /library/books}. */
final class BookSpecifications {

    private BookSpecifications() {
    }

    static Specification<Book> matches(String search) {
        String pattern = "%" + search.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("title")), pattern),
                cb.like(cb.lower(root.get("author")), pattern),
                cb.like(cb.lower(cb.coalesce(root.get("isbn"), "")), pattern));
    }
}
