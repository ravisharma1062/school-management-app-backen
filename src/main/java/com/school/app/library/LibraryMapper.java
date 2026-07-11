package com.school.app.library;

import org.springframework.stereotype.Component;

@Component
public class LibraryMapper {

    public BookDto toDto(Book book) {
        return new BookDto(book.getId(), book.getTitle(), book.getAuthor(), book.getIsbn(),
                book.getTotalCopies(), book.getAvailableCopies());
    }

    public BookIssueDto toDto(BookIssue issue) {
        return new BookIssueDto(
                issue.getId(),
                issue.getBook().getId(),
                issue.getBook().getTitle(),
                issue.getStudent().getId(),
                issue.getStudent().getName(),
                issue.getIssuedAt(),
                issue.getDueDate(),
                issue.getReturnedAt(),
                issue.getFineAmount(),
                issue.getStatus());
    }
}
