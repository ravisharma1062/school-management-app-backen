package com.school.app.student;

import com.school.app.common.exception.BadRequestException;
import com.school.app.common.exception.ResourceNotFoundException;
import com.school.app.user.Role;
import com.school.app.user.User;
import com.school.app.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudentService {

    private static final List<String> REQUIRED_CSV_COLUMNS =
            List.of("name", "rollNo", "studentClass", "section", "dob");

    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final StudentMapper studentMapper;

    public Page<StudentDto> list(String name, String rollNo, String studentClass, boolean includeArchived, Pageable pageable) {
        Specification<Student> spec = Specification.allOf();
        if (!includeArchived) {
            spec = spec.and(StudentSpecifications.active());
        }
        if (name != null && !name.isBlank()) {
            spec = spec.and(StudentSpecifications.nameContains(name));
        }
        if (rollNo != null && !rollNo.isBlank()) {
            spec = spec.and(StudentSpecifications.rollNoContains(rollNo));
        }
        if (studentClass != null && !studentClass.isBlank()) {
            spec = spec.and(StudentSpecifications.studentClassEquals(studentClass));
        }
        return studentRepository.findAll(spec, pageable).map(studentMapper::toDto);
    }

    public StudentDto create(StudentCreateRequest request) {
        if (studentRepository.existsByStudentClassAndSectionAndRollNo(
                request.studentClass(), request.section(), request.rollNo())) {
            throw new BadRequestException("A student with this roll number already exists in this class/section");
        }

        User parent = null;
        if (request.parentId() != null) {
            parent = userRepository.findById(request.parentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent user with id " + request.parentId() + " not found"));
        }

        Student student = Student.builder()
                .name(request.name())
                .rollNo(request.rollNo())
                .studentClass(request.studentClass())
                .section(request.section())
                .dob(request.dob())
                .parent(parent)
                .build();

        return studentMapper.toDto(studentRepository.save(student));
    }

    public List<StudentDto> getMyChildren(User currentUser) {
        return studentRepository.findByParentIdAndActiveTrue(currentUser.getId()).stream()
                .map(studentMapper::toDto)
                .toList();
    }

    public StudentDto getById(UUID id, User currentUser) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Student with id " + id + " not found"));

        if (currentUser.getRole() == Role.PARENT
                && (student.getParent() == null || !student.getParent().getId().equals(currentUser.getId()))) {
            throw new AccessDeniedException("Parents may only view their own child's record");
        }

        return studentMapper.toDto(student);
    }

    public StudentDto update(UUID id, StudentUpdateRequest request) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Student with id " + id + " not found"));

        if (request.name() != null) {
            student.setName(request.name());
        }
        if (request.rollNo() != null) {
            student.setRollNo(request.rollNo());
        }
        if (request.studentClass() != null) {
            student.setStudentClass(request.studentClass());
        }
        if (request.section() != null) {
            student.setSection(request.section());
        }
        if (request.dob() != null) {
            student.setDob(request.dob());
        }
        if (request.parentId() != null) {
            User parent = userRepository.findById(request.parentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent user with id " + request.parentId() + " not found"));
            student.setParent(parent);
        }

        return studentMapper.toDto(studentRepository.save(student));
    }

    public StudentDto archive(UUID id) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Student with id " + id + " not found"));
        student.setActive(false);
        return studentMapper.toDto(studentRepository.save(student));
    }

    public StudentDto restore(UUID id) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Student with id " + id + " not found"));
        student.setActive(true);
        return studentMapper.toDto(studentRepository.save(student));
    }

    /**
     * Imports students from a CSV file, row by row. A bad row (validation failure, unknown
     * parent email, duplicate roll number) is recorded as an error and skipped — it does not
     * fail the rest of the batch, since each row is saved via its own repository call rather
     * than one transaction wrapping the whole file.
     *
     * <p>Expected header row: {@code name,rollNo,studentClass,section,dob[,parentEmail]}.
     * {@code dob} must be {@code yyyy-MM-dd}; {@code parentEmail} is optional.
     */
    public BulkImportResult bulkImport(MultipartFile file) {
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setTrim(true)
                .setIgnoreEmptyLines(true)
                .build();

        List<BulkImportResult.RowError> errors = new ArrayList<>();
        // Tracks (class, section, rollNo) already imported in this file, since two rows in the
        // same batch could collide with each other before either has hit the DB uniqueness check.
        Set<String> seenInFile = new HashSet<>();
        int total = 0;
        int success = 0;

        try (CSVParser parser = format.parse(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            for (String required : REQUIRED_CSV_COLUMNS) {
                if (!parser.getHeaderNames().contains(required)) {
                    throw new BadRequestException("CSV is missing required column: " + required);
                }
            }

            for (CSVRecord record : parser) {
                total++;
                int rowNumber = (int) record.getRecordNumber() + 1; // +1 to account for the header row

                try {
                    importRow(record, seenInFile);
                    success++;
                } catch (Exception e) {
                    errors.add(new BulkImportResult.RowError(rowNumber, e.getMessage()));
                }
            }
        } catch (IOException e) {
            throw new BadRequestException("Could not read the uploaded CSV file: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            // Thrown by commons-csv itself for a malformed CSV (e.g. inconsistent column counts).
            throw new BadRequestException("Malformed CSV file: " + e.getMessage());
        }

        return new BulkImportResult(total, success, total - success, errors);
    }

    private void importRow(CSVRecord record, Set<String> seenInFile) {
        String name = record.get("name");
        String rollNo = record.get("rollNo");
        String studentClass = record.get("studentClass");
        String section = record.get("section");
        String dobRaw = record.get("dob");

        if (name.isBlank() || rollNo.isBlank() || studentClass.isBlank() || section.isBlank() || dobRaw.isBlank()) {
            throw new IllegalArgumentException("name, rollNo, studentClass, section and dob are all required");
        }

        LocalDate dob;
        try {
            dob = LocalDate.parse(dobRaw);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("dob '" + dobRaw + "' is not a valid yyyy-MM-dd date");
        }

        String fileKey = studentClass + "|" + section + "|" + rollNo;
        if (!seenInFile.add(fileKey)) {
            throw new IllegalArgumentException("Duplicate roll number within this file for " + studentClass + "-" + section);
        }
        if (studentRepository.existsByStudentClassAndSectionAndRollNo(studentClass, section, rollNo)) {
            throw new IllegalArgumentException("A student with this roll number already exists in this class/section");
        }

        User parent = null;
        boolean hasParentColumn = record.isMapped("parentEmail");
        if (hasParentColumn && !record.get("parentEmail").isBlank()) {
            String parentEmail = record.get("parentEmail");
            parent = userRepository.findByEmail(parentEmail)
                    .orElseThrow(() -> new IllegalArgumentException("No user found with email " + parentEmail));
        }

        Student student = Student.builder()
                .name(name)
                .rollNo(rollNo)
                .studentClass(studentClass)
                .section(section)
                .dob(dob)
                .parent(parent)
                .build();

        studentRepository.save(student);
    }
}
