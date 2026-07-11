package com.school.app.library;

import com.school.app.common.exception.BadRequestException;
import com.school.app.common.exception.ResourceNotFoundException;
import com.school.app.common.storage.FileStorageService;
import com.school.app.student.Student;
import com.school.app.student.StudentRepository;
import com.school.app.user.Role;
import com.school.app.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LibraryService {

    private static final int LOAN_PERIOD_DAYS = 14;
    private static final String COVER_STORAGE_SUBDIRECTORY = "book-covers";

    private final BookRepository bookRepository;
    private final BookIssueRepository bookIssueRepository;
    private final StudentRepository studentRepository;
    private final FileStorageService fileStorageService;
    private final LibraryMapper libraryMapper;

    public BookDto createBook(BookCreateRequest request) {
        Book book = Book.builder()
                .title(request.title())
                .author(request.author())
                .isbn(request.isbn())
                .totalCopies(request.totalCopies())
                .availableCopies(request.totalCopies())
                .build();
        return libraryMapper.toDto(bookRepository.save(book));
    }

    public Page<BookDto> searchBooks(String search, Pageable pageable) {
        Specification<Book> spec = (search == null || search.isBlank())
                ? Specification.where(null)
                : BookSpecifications.matches(search.trim());
        return bookRepository.findAll(spec, pageable).map(libraryMapper::toDto);
    }

    public BookIssueDto issueBook(BookIssueCreateRequest request) {
        Book book = bookRepository.findById(request.bookId())
                .orElseThrow(() -> new ResourceNotFoundException("Book with id " + request.bookId() + " not found"));
        Student student = studentRepository.findById(request.studentId())
                .orElseThrow(() -> new ResourceNotFoundException("Student with id " + request.studentId() + " not found"));

        if (book.getAvailableCopies() <= 0) {
            throw new BadRequestException("No copies of \"" + book.getTitle() + "\" are currently available");
        }
        if (bookIssueRepository.existsByBookIdAndStudentIdAndStatus(book.getId(), student.getId(), BookIssueStatus.ISSUED)) {
            throw new BadRequestException("This student already has this book on loan");
        }

        book.setAvailableCopies(book.getAvailableCopies() - 1);
        bookRepository.save(book);

        LocalDate today = LocalDate.now();
        BookIssue issue = BookIssue.builder()
                .book(book)
                .student(student)
                .issuedAt(today)
                .dueDate(today.plusDays(LOAN_PERIOD_DAYS))
                .status(BookIssueStatus.ISSUED)
                .build();

        BookIssue saved = bookIssueRepository.save(issue);
        saved.setBook(book);
        saved.setStudent(student);
        return libraryMapper.toDto(saved);
    }

    public BookIssueDto returnBook(UUID issueId) {
        BookIssue issue = bookIssueRepository.findByIdFetchBookAndStudent(issueId)
                .orElseThrow(() -> new ResourceNotFoundException("Book issue with id " + issueId + " not found"));

        if (issue.getStatus() == BookIssueStatus.RETURNED) {
            throw new BadRequestException("This book has already been returned");
        }

        LocalDate today = LocalDate.now();
        issue.setReturnedAt(today);
        issue.setFineAmount(FineCalculator.calculateFine(issue.getDueDate(), today));
        issue.setStatus(BookIssueStatus.RETURNED);

        Book book = issue.getBook();
        book.setAvailableCopies(book.getAvailableCopies() + 1);
        bookRepository.save(book);

        BookIssue saved = bookIssueRepository.save(issue);
        saved.setBook(book);
        saved.setStudent(issue.getStudent());
        return libraryMapper.toDto(saved);
    }

    public BookDto uploadCover(UUID bookId, MultipartFile file) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new ResourceNotFoundException("Book with id " + bookId + " not found"));

        book.setCoverImageKey(fileStorageService.store(file, COVER_STORAGE_SUBDIRECTORY));
        book.setCoverImageContentType(file.getContentType() != null ? file.getContentType() : "application/octet-stream");
        return libraryMapper.toDto(bookRepository.save(book));
    }

    public StoredCover downloadCover(UUID bookId) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new ResourceNotFoundException("Book with id " + bookId + " not found"));

        if (book.getCoverImageKey() == null) {
            throw new ResourceNotFoundException("Book with id " + bookId + " has no cover image");
        }
        return new StoredCover(fileStorageService.load(book.getCoverImageKey()), book.getCoverImageContentType());
    }

    public record StoredCover(byte[] content, String contentType) {
    }

    public List<BookIssueDto> getIssuesForStudent(UUID studentId, User currentUser) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student with id " + studentId + " not found"));

        if (currentUser.getRole() == Role.PARENT
                && (student.getParent() == null || !student.getParent().getId().equals(currentUser.getId()))) {
            throw new AccessDeniedException("Parents may only view their own child's issued books");
        }

        return bookIssueRepository.findByStudentIdFetchBookAndStudent(studentId).stream()
                .map(libraryMapper::toDto)
                .toList();
    }
}
