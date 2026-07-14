package com.school.app.export;

import com.school.app.attendance.Attendance;
import com.school.app.attendance.AttendanceRepository;
import com.school.app.common.exception.ResourceNotFoundException;
import com.school.app.common.security.TenantContext;
import com.school.app.event.Event;
import com.school.app.event.EventRepository;
import com.school.app.event.EventRsvp;
import com.school.app.event.EventRsvpRepository;
import com.school.app.examresult.ExamResult;
import com.school.app.examresult.ExamResultRepository;
import com.school.app.fee.Fee;
import com.school.app.fee.FeeRepository;
import com.school.app.fee.payment.Payment;
import com.school.app.fee.payment.PaymentRepository;
import com.school.app.homework.Homework;
import com.school.app.homework.HomeworkRepository;
import com.school.app.homework.submission.HomeworkSubmission;
import com.school.app.homework.submission.HomeworkSubmissionRepository;
import com.school.app.leaverequest.LeaveRequest;
import com.school.app.leaverequest.LeaveRequestRepository;
import com.school.app.library.Book;
import com.school.app.library.BookIssue;
import com.school.app.library.BookIssueRepository;
import com.school.app.library.BookRepository;
import com.school.app.messaging.Conversation;
import com.school.app.messaging.ConversationRepository;
import com.school.app.messaging.Message;
import com.school.app.messaging.MessageRepository;
import com.school.app.notice.Notice;
import com.school.app.notice.NoticeRepository;
import com.school.app.school.School;
import com.school.app.school.SchoolRepository;
import com.school.app.student.Student;
import com.school.app.student.StudentRepository;
import com.school.app.timetable.Timetable;
import com.school.app.timetable.TimetableRepository;
import com.school.app.user.Teacher;
import com.school.app.user.TeacherRepository;
import com.school.app.user.User;
import com.school.app.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * MT-6d — an ADMIN-triggered export of everything this tenant has entered into the app, as a ZIP
 * of CSVs (one entry per entity). Deliberately excludes purely operational/system data that isn't
 * "the school's own data" in the DPDP sense (notification-send logs, platform audit trail, GPS bus
 * tracking, activation tokens, password hashes) — this is the school's content, not our logs.
 *
 * <p>No automatic retention/deletion is implemented for cancelled tenants: the plan calls for that
 * policy to be defined "with legal input" given minors' data is involved, which hasn't happened
 * yet. This export is the buildable half of MT-6d's Definition of Done; the deletion-policy half
 * is intentionally deferred.
 *
 * <p>Runs as one read-only transaction so every lazily-loaded association (student names on
 * attendance rows, etc.) resolves while the Hibernate Session is still open — open-in-view is
 * disabled app-wide, so this can't be split across multiple calls.
 */
@Service
@RequiredArgsConstructor
public class DataExportService {

    private final SchoolRepository schoolRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final TeacherRepository teacherRepository;
    private final AttendanceRepository attendanceRepository;
    private final HomeworkRepository homeworkRepository;
    private final HomeworkSubmissionRepository homeworkSubmissionRepository;
    private final FeeRepository feeRepository;
    private final PaymentRepository paymentRepository;
    private final ExamResultRepository examResultRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final BookRepository bookRepository;
    private final BookIssueRepository bookIssueRepository;
    private final NoticeRepository noticeRepository;
    private final EventRepository eventRepository;
    private final EventRsvpRepository eventRsvpRepository;
    private final TimetableRepository timetableRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    public record ExportResult(String filename, byte[] zipBytes) {
    }

    @Transactional(readOnly = true)
    public ExportResult exportCurrentSchool() {
        School school = schoolRepository.findById(TenantContext.get())
                .orElseThrow(() -> new ResourceNotFoundException("School not found"));

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
            writeCsv(zip, "students.csv", List.of("id", "name", "rollNo", "class", "section", "dob", "parentEmail", "active", "createdAt"),
                    studentRepository.findAll(), (Student s) -> List.of(
                            str(s.getId()), s.getName(), s.getRollNo(), s.getStudentClass(), s.getSection(),
                            str(s.getDob()), s.getParent() != null ? s.getParent().getEmail() : "",
                            str(s.isActive()), str(s.getCreatedAt())));

            writeCsv(zip, "users.csv", List.of("id", "name", "email", "role", "phone", "status", "createdAt"),
                    userRepository.findAll(), (User u) -> List.of(
                            str(u.getId()), u.getName(), u.getEmail(), str(u.getRole()), str(u.getPhone()),
                            str(u.getStatus()), str(u.getCreatedAt())));

            writeCsv(zip, "teachers.csv", List.of("id", "teacherEmail", "subjects", "classesAssigned"),
                    teacherRepository.findAll(), (Teacher t) -> List.of(
                            str(t.getId()), t.getUser() != null ? t.getUser().getEmail() : "",
                            str(t.getSubjects()), str(t.getClassesAssigned())));

            writeCsv(zip, "attendance.csv", List.of("id", "studentRollNo", "date", "status", "markedByEmail"),
                    attendanceRepository.findAll(), (Attendance a) -> List.of(
                            str(a.getId()), a.getStudent().getRollNo(), str(a.getDate()), str(a.getStatus()),
                            a.getMarkedBy().getEmail()));

            writeCsv(zip, "homework.csv", List.of("id", "class", "section", "subject", "title", "description", "dueDate", "createdByEmail", "createdAt"),
                    homeworkRepository.findAll(), (Homework h) -> List.of(
                            str(h.getId()), h.getStudentClass(), h.getSection(), h.getSubject(), h.getTitle(),
                            str(h.getDescription()), str(h.getDueDate()),
                            h.getCreatedBy() != null ? h.getCreatedBy().getEmail() : "", str(h.getCreatedAt())));

            writeCsv(zip, "homework_submissions.csv", List.of("id", "homeworkTitle", "studentRollNo", "fileName", "status", "teacherFeedback", "grade", "submittedAt"),
                    homeworkSubmissionRepository.findAll(), (HomeworkSubmission hs) -> List.of(
                            str(hs.getId()), hs.getHomework().getTitle(), hs.getStudent().getRollNo(), hs.getFileName(),
                            str(hs.getStatus()), str(hs.getTeacherFeedback()), str(hs.getGrade()), str(hs.getSubmittedAt())));

            writeCsv(zip, "fees.csv", List.of("id", "studentRollNo", "term", "amountDue", "amountPaid", "status", "dueDate"),
                    feeRepository.findAll(), (Fee f) -> List.of(
                            str(f.getId()), f.getStudent().getRollNo(), f.getTerm(), str(f.getAmountDue()),
                            str(f.getAmountPaid()), str(f.getStatus()), str(f.getDueDate())));

            writeCsv(zip, "payments.csv", List.of("id", "studentRollNo", "amount", "gatewayOrderId", "gatewayPaymentId", "status", "initiatedByEmail", "paidAt", "createdAt"),
                    paymentRepository.findAll(), (Payment p) -> List.of(
                            str(p.getId()), p.getFee().getStudent().getRollNo(), str(p.getAmount()), str(p.getGatewayOrderId()),
                            str(p.getGatewayPaymentId()), str(p.getStatus()),
                            p.getInitiatedBy() != null ? p.getInitiatedBy().getEmail() : "", str(p.getPaidAt()), str(p.getCreatedAt())));

            writeCsv(zip, "exam_results.csv", List.of("id", "studentRollNo", "subject", "examName", "marksObtained", "maxMarks", "grade", "term"),
                    examResultRepository.findAll(), (ExamResult e) -> List.of(
                            str(e.getId()), e.getStudent().getRollNo(), e.getSubject(), e.getExamName(),
                            str(e.getMarksObtained()), str(e.getMaxMarks()), e.getGrade(), e.getTerm()));

            writeCsv(zip, "leave_requests.csv", List.of("id", "requesterEmail", "type", "fromDate", "toDate", "reason", "status", "reviewedByEmail", "createdAt"),
                    leaveRequestRepository.findAll(), (LeaveRequest lr) -> List.of(
                            str(lr.getId()), lr.getRequester().getEmail(), str(lr.getType()), str(lr.getFromDate()),
                            str(lr.getToDate()), str(lr.getReason()), str(lr.getStatus()),
                            lr.getReviewedBy() != null ? lr.getReviewedBy().getEmail() : "", str(lr.getCreatedAt())));

            writeCsv(zip, "books.csv", List.of("id", "title", "author", "isbn", "totalCopies", "availableCopies", "createdAt"),
                    bookRepository.findAll(), (Book b) -> List.of(
                            str(b.getId()), b.getTitle(), b.getAuthor(), str(b.getIsbn()),
                            str(b.getTotalCopies()), str(b.getAvailableCopies()), str(b.getCreatedAt())));

            writeCsv(zip, "book_issues.csv", List.of("id", "bookTitle", "studentRollNo", "issuedAt", "dueDate", "returnedAt", "fineAmount", "status"),
                    bookIssueRepository.findAll(), (BookIssue bi) -> List.of(
                            str(bi.getId()), bi.getBook().getTitle(), bi.getStudent().getRollNo(), str(bi.getIssuedAt()),
                            str(bi.getDueDate()), str(bi.getReturnedAt()), str(bi.getFineAmount()), str(bi.getStatus())));

            writeCsv(zip, "notices.csv", List.of("id", "title", "description", "targetRole", "createdByEmail", "active", "createdAt"),
                    noticeRepository.findAll(), (Notice n) -> List.of(
                            str(n.getId()), n.getTitle(), str(n.getDescription()), str(n.getTargetRole()),
                            n.getCreatedBy() != null ? n.getCreatedBy().getEmail() : "", str(n.isActive()), str(n.getCreatedAt())));

            writeCsv(zip, "events.csv", List.of("id", "title", "description", "eventDate", "location", "createdByEmail", "createdAt"),
                    eventRepository.findAll(), (Event e) -> List.of(
                            str(e.getId()), e.getTitle(), str(e.getDescription()), str(e.getEventDate()), str(e.getLocation()),
                            e.getCreatedBy() != null ? e.getCreatedBy().getEmail() : "", str(e.getCreatedAt())));

            writeCsv(zip, "event_rsvps.csv", List.of("id", "eventTitle", "userEmail", "status", "respondedAt"),
                    eventRsvpRepository.findAll(), (EventRsvp r) -> List.of(
                            str(r.getId()), r.getEvent().getTitle(), r.getUser().getEmail(), str(r.getStatus()), str(r.getRespondedAt())));

            writeCsv(zip, "timetable.csv", List.of("id", "class", "section", "dayOfWeek", "period", "subject", "teacherEmail", "active"),
                    timetableRepository.findAll(), (Timetable t) -> List.of(
                            str(t.getId()), t.getStudentClass(), t.getSection(), str(t.getDayOfWeek()), str(t.getPeriod()),
                            t.getSubject(), t.getTeacher() != null && t.getTeacher().getUser() != null ? t.getTeacher().getUser().getEmail() : "",
                            str(t.isActive())));

            writeCsv(zip, "conversations.csv", List.of("id", "parentEmail", "teacherEmail", "createdAt"),
                    conversationRepository.findAll(), (Conversation c) -> List.of(
                            str(c.getId()), c.getParent().getEmail(), c.getTeacher().getEmail(), str(c.getCreatedAt())));

            writeCsv(zip, "messages.csv", List.of("id", "conversationId", "senderEmail", "body", "sentAt"),
                    messageRepository.findAll(), (Message m) -> List.of(
                            str(m.getId()), str(m.getConversation().getId()), m.getSender().getEmail(), m.getBody(), str(m.getSentAt())));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return new ExportResult(school.getSlug() + "-export.zip", buffer.toByteArray());
    }

    private <T> void writeCsv(ZipOutputStream zip, String entryName, List<String> header, List<T> rows, Function<T, List<String>> rowMapper) throws IOException {
        zip.putNextEntry(new ZipEntry(entryName));
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", header)).append('\n');
        for (T row : rows) {
            sb.append(rowMapper.apply(row).stream().map(this::csvEscape).reduce((a, b) -> a + "," + b).orElse("")).append('\n');
        }
        zip.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private String csvEscape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String str(Object value) {
        return value == null ? "" : value.toString();
    }
}
