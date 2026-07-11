package com.school.app.library;

import com.school.app.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/library")
@RequiredArgsConstructor
@Tag(name = "Library")
public class LibraryController {

    private final LibraryService libraryService;

    @PostMapping("/books")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Add a book to the library catalog")
    public BookDto createBook(@Valid @RequestBody BookCreateRequest request) {
        return libraryService.createBook(request);
    }

    @GetMapping("/books")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'PARENT')")
    @Operation(summary = "Search the library catalog by title, author, or ISBN")
    public Page<BookDto> searchBooks(@RequestParam(required = false) String search, Pageable pageable) {
        return libraryService.searchBooks(search, pageable);
    }

    @PostMapping("/issues")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Issue a book to a student")
    public BookIssueDto issueBook(@Valid @RequestBody BookIssueCreateRequest request) {
        return libraryService.issueBook(request);
    }

    @PostMapping("/issues/{issueId}/return")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Return a book, computing any overdue fine")
    public BookIssueDto returnBook(@PathVariable UUID issueId) {
        return libraryService.returnBook(issueId);
    }

    @GetMapping("/students/{studentId}/issues")
    @PreAuthorize("hasAnyRole('ADMIN', 'PARENT')")
    @Operation(summary = "List a student's book issue history")
    public List<BookIssueDto> getIssuesForStudent(
            @PathVariable UUID studentId, @AuthenticationPrincipal User currentUser) {
        return libraryService.getIssuesForStudent(studentId, currentUser);
    }
}
