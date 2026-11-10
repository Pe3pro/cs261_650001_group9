package com.example.demo.service;

import com.example.demo.dao.EligibilityRecordDao;
import com.example.demo.dao.StudentRepository;
import com.example.demo.dto.EligibilityRequest;
import com.example.demo.dto.EligibilityResponse;
import com.example.demo.entity.EligibilityRecord;
import com.example.demo.entity.Student;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class EligibilityService {

    private static final Map<String, Double> GRADE_POINTS =
        Map.of(
            "A", 4.0,
            "B+", 3.5,
            "B", 3.0,
            "C+", 2.5,
            "C", 2.0,
            "D+", 1.5,
            "D", 1.0,
            "F", 0.0,
            "ยังไม่เคยเรียน", -1.0
        );

    private static final Set<String> ALLOWED_STATUSES =
        Set.of(
            "ยังไม่เคยลง",
            "กำลังศึกษา",
            "เคยศึกษาแล้ว"
        );

    private static final List<String> GRADE_COURSES_61 =
        List.of(
            "คพ.101",
            "คพ.102",
            "คพ.111",
            "คพ.251",
            "คพ.264"
        );

    private static final List<String> GRADE_COURSES_66 =
        List.of(
            "คพ.100",
            "คพ.101",
            "คพ.102",
            "คพ.111",
            "คพ.251",
            "คพ.261"
        );

    private static final Set<String> ALTERNATIVE_GRADE_COURSES =
        Set.of(
            "คพ.213",
            "คพ.216"
        );

    private static final Set<String> STATUS_COURSES_61 =
        Set.of(
            "คพ.384",
            "คพ.266",
            "คพ.322",
            "คพ.348"
        );

    private static final Set<String> STATUS_COURSES_66 =
        Set.of(
            "คพ.180",
            "คพ.262",
            "คพ.331",
            "คพ.240"
        );

    private final EligibilityRecordDao eligibilityRecordDao;
    private final StudentRepository studentRepository;

    public EligibilityService(
        EligibilityRecordDao eligibilityRecordDao,
        StudentRepository studentRepository
    ) {
        this.eligibilityRecordDao = eligibilityRecordDao;
        this.studentRepository = studentRepository;
    }

    public EligibilityResponse check(
        Long userId,
        EligibilityRequest request
    ) {
        if (userId == null) {
            return invalidResponse(
                List.of("กรุณาส่ง X-User-Id")
            );
        }

        Student student =
            studentRepository.findByUserId(userId)
                .orElse(null);

        if (student == null) {
            return invalidResponse(
                List.of(
                    "ไม่พบข้อมูลนักศึกษาของผู้ใช้ที่เข้าสู่ระบบ"
                )
            );
        }

        List<String> errors =
            validate(request, student);

        if (!errors.isEmpty()) {
            return invalidResponse(errors);
        }

        String curriculum =
            curriculumFromStudent(student);

        double databaseGpa = student.getGpa();

        List<String> reasons = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (databaseGpa < 2.75) {
            reasons.add(
                "GPA ต้องไม่ต่ำกว่า 2.75"
            );
        }

        request.grades().forEach(
            (course, grade) -> {
                if (GRADE_POINTS.get(grade) < 2.5) {
                    reasons.add(
                        course +
                        " ต้องได้เกรด C+ ขึ้นไป"
                    );
                }
            }
        );

        request.courseStatuses().forEach(
            (course, status) -> {
                if ("ยังไม่เคยลง".equals(status)) {
                    reasons.add(
                        course +
                        " ต้องกำลังศึกษา หรือเคยศึกษาแล้ว"
                    );
                }
            }
        );

        boolean acknowledgedAll =
            request.acknowledgements()
                .stream()
                .allMatch(Boolean.TRUE::equals);

        if (!acknowledgedAll) {
            reasons.add(
                "ต้องยืนยันเงื่อนไขให้ครบทั้ง 3 ข้อ"
            );
        }

        if ("61".equals(curriculum)) {
            warnings.add(
                "ต้องสอบได้วิชา คพ.302 " +
                "ก่อนออกปฏิบัติงาน"
            );
        } else {
            warnings.add(
                "ต้องสอบได้วิชา คพ.301 " +
                "ก่อนออกปฏิบัติงาน"
            );
        }

        boolean eligible = reasons.isEmpty();

        EligibilityResponse response =
            new EligibilityResponse(
                true,
                eligible,
                eligible
                    ? "ผ่านคุณสมบัติ"
                    : "ไม่ผ่านคุณสมบัติ",
                List.of(),
                reasons,
                warnings
            );

        /*
         * ใช้รหัสจากฐานข้อมูลเท่านั้น
         * ไม่ใช้รหัสที่ผู้ใช้ส่งมาเป็นค่าที่เชื่อถือได้
         */
        EligibilityRecord record =
            new EligibilityRecord(
                student.getStudentId(),
                eligible,
                response.message(),
                LocalDateTime.now()
            );

        eligibilityRecordDao.save(record);

        return response;
    }

    private EligibilityResponse invalidResponse(
        List<String> errors
    ) {
        return new EligibilityResponse(
            false,
            null,
            "ข้อมูลไม่ถูกต้อง",
            errors,
            List.of(),
            List.of()
        );
    }

    private List<String> validate(
        EligibilityRequest request,
        Student student
    ) {
        List<String> errors = new ArrayList<>();

        if (request == null) {
            errors.add(
                "กรุณาส่งข้อมูลสำหรับตรวจสอบ"
            );
            return errors;
        }

        String databaseCurriculum =
            curriculumFromStudent(student);

        if (databaseCurriculum == null) {
            errors.add(
                "รหัสนักศึกษาในฐานข้อมูลไม่ถูกต้อง"
            );
            return errors;
        }

        validateStudentId(
            request,
            student,
            errors
        );

        validateCurriculum(
            request,
            databaseCurriculum,
            errors
        );

        validateGpax(
            request,
            student,
            errors
        );

        validateGrades(
            request,
            databaseCurriculum,
            errors
        );

        validateCourseStatuses(
            request,
            databaseCurriculum,
            errors
        );

        validateAcknowledgements(
            request,
            errors
        );

        return errors;
    }

    private void validateStudentId(
        EligibilityRequest request,
        Student student,
        List<String> errors
    ) {
        if (isBlank(request.studentId())) {
            errors.add(
                "กรุณากรอกรหัสนักศึกษา"
            );
            return;
        }

        if (!request.studentId().matches("\\d{10}")) {
            errors.add(
                "รหัสนักศึกษาต้องเป็นตัวเลข 10 หลัก"
            );
            return;
        }

        if (
            !student.getStudentId()
                .equals(request.studentId())
        ) {
            errors.add(
                "รหัสนักศึกษาไม่ตรงกับผู้ใช้ที่เข้าสู่ระบบ"
            );
        }
    }

    private void validateCurriculum(
        EligibilityRequest request,
        String databaseCurriculum,
        List<String> errors
    ) {
        boolean validCurriculum =
            "61".equals(request.curriculum()) ||
            "66".equals(request.curriculum());

        if (!validCurriculum) {
            errors.add(
                "หลักสูตรต้องเป็น 61 หรือ 66"
            );
            return;
        }

        if (
            !databaseCurriculum.equals(
                request.curriculum()
            )
        ) {
            errors.add(
                "หลักสูตรไม่ตรงกับข้อมูลนักศึกษา"
            );
        }
    }

    private void validateGpax(
        EligibilityRequest request,
        Student student,
        List<String> errors
    ) {
        if (isBlank(request.gpax())) {
            errors.add("กรุณากรอก GPA");
            return;
        }

        String gpaxPattern =
            "(?:[0-3](?:\\.\\d{1,2})?" +
            "|4(?:\\.0{1,2})?)";

        if (!request.gpax().matches(gpaxPattern)) {
            errors.add(
                "GPA ต้องเป็นตัวเลข 0.00–4.00 " +
                "และมีทศนิยมไม่เกิน 2 ตำแหน่ง"
            );
            return;
        }

        if (student.getGpa() == null) {
            errors.add(
                "ไม่พบ GPA ของนักศึกษาในฐานข้อมูล"
            );
            return;
        }

        BigDecimal requestGpa =
            new BigDecimal(request.gpax());

        BigDecimal databaseGpa =
            BigDecimal.valueOf(student.getGpa());

        if (requestGpa.compareTo(databaseGpa) != 0) {
            errors.add(
                "GPA ไม่ตรงกับข้อมูลในฐานข้อมูล"
            );
        }
    }

    private void validateGrades(
        EligibilityRequest request,
        String curriculum,
        List<String> errors
    ) {
        if (
            request.grades() == null ||
            request.grades().isEmpty()
        ) {
            errors.add(
                "กรุณาส่งผลการเรียนรายวิชาบังคับ"
            );
            return;
        }

        List<String> fixedCourses =
            "61".equals(curriculum)
                ? GRADE_COURSES_61
                : GRADE_COURSES_66;

        Set<String> allowedCourses =
            new HashSet<>(fixedCourses);

        allowedCourses.addAll(
            ALTERNATIVE_GRADE_COURSES
        );

        for (String course : fixedCourses) {
            if (
                !request.grades()
                    .containsKey(course)
            ) {
                errors.add(
                    "ไม่พบผลการเรียนวิชา " +
                    course
                );
            }
        }

        boolean hasAlternativeCourse =
            ALTERNATIVE_GRADE_COURSES
                .stream()
                .anyMatch(
                    request.grades()::containsKey
                );

        if (!hasAlternativeCourse) {
            errors.add(
                "ต้องส่งผลการเรียนวิชา คพ.213 " +
                "หรือ คพ.216 อย่างน้อย 1 วิชา"
            );
        }

        request.grades().forEach(
            (course, grade) -> {
                if (!allowedCourses.contains(course)) {
                    errors.add(
                        "รายวิชา " +
                        course +
                        " ไม่อยู่ในหลักสูตร " +
                        curriculum
                    );
                }

                if (
                    grade == null ||
                    !GRADE_POINTS.containsKey(grade)
                ) {
                    errors.add(
                        "เกรดของวิชา " +
                        course +
                        " ไม่ถูกต้อง"
                    );
                }
            }
        );
    }

    private void validateCourseStatuses(
        EligibilityRequest request,
        String curriculum,
        List<String> errors
    ) {
        if (
            request.courseStatuses() == null ||
            request.courseStatuses().isEmpty()
        ) {
            errors.add(
                "กรุณาส่งสถานะรายวิชาที่กำลังศึกษา " +
                "หรือเคยศึกษา"
            );
            return;
        }

        String fixedCourse =
            "61".equals(curriculum)
                ? "คพ.384"
                : "คพ.180";

        Set<String> allowedCourses =
            "61".equals(curriculum)
                ? STATUS_COURSES_61
                : STATUS_COURSES_66;

        Set<String> alternativeCourses =
            new HashSet<>(allowedCourses);

        alternativeCourses.remove(fixedCourse);

        if (
            !request.courseStatuses()
                .containsKey(fixedCourse)
        ) {
            errors.add(
                "ไม่พบสถานะรายวิชา " +
                fixedCourse
            );
        }

        boolean hasAlternativeCourse =
            alternativeCourses.stream()
                .anyMatch(
                    request.courseStatuses()
                        ::containsKey
                );

        if (!hasAlternativeCourse) {
            errors.add(
                "ต้องส่งสถานะรายวิชาในกลุ่มวิชาเลือก" +
                "อย่างน้อย 1 วิชา"
            );
        }

        request.courseStatuses().forEach(
            (course, status) -> {
                if (!allowedCourses.contains(course)) {
                    errors.add(
                        "รายวิชา " +
                        course +
                        " ไม่อยู่ในหลักสูตร " +
                        curriculum
                    );
                }

                if (
                    status == null ||
                    !ALLOWED_STATUSES.contains(status)
                ) {
                    errors.add(
                        "สถานะของวิชา " +
                        course +
                        " ไม่ถูกต้อง"
                    );
                }
            }
        );
    }

    private void validateAcknowledgements(
        EligibilityRequest request,
        List<String> errors
    ) {
        if (
            request.acknowledgements() == null ||
            request.acknowledgements().size() != 3
        ) {
            errors.add(
                "ต้องส่งข้อมูลการยืนยันให้ครบ 3 ข้อ"
            );
            return;
        }

        boolean containsNull =
            request.acknowledgements()
                .stream()
                .anyMatch(value -> value == null);

        if (containsNull) {
            errors.add(
                "ข้อมูลการยืนยันต้องเป็น true หรือ false"
            );
        }
    }

    /*
     * นักศึกษารหัสตั้งแต่ 66 เป็นต้นไป
     * ใช้หลักสูตร 66
     * รหัสก่อนหน้าใช้หลักสูตร 61
     */
    private String curriculumFromStudent(
        Student student
    ) {
        String studentId = student.getStudentId();

        if (
            studentId == null ||
            !studentId.matches("\\d{10}")
        ) {
            return null;
        }

        int admissionYear =
            Integer.parseInt(
                studentId.substring(0, 2)
            );

        return admissionYear >= 66
            ? "66"
            : "61";
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}