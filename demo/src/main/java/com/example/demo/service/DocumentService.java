package com.example.demo.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.example.demo.dao.ApplicationRepository;
import com.example.demo.dao.DocumentRepository;
import com.example.demo.dao.StudentRepository;
import com.example.demo.dto.DocumentResponse;
import com.example.demo.entity.Application;
import com.example.demo.entity.Document;
import com.example.demo.entity.Role;
import com.example.demo.entity.Student;
import com.example.demo.entity.User;

/**
 * U3: อัปโหลด / เปิดดูไฟล์ Transcript (PDF)
 * ตรวจทั้งนามสกุล ขนาด และเนื้อไฟล์ (ต้องขึ้นต้นด้วย %PDF-) ก่อนบันทึกลงที่เก็บไฟล์
 */
@Service
@Transactional
public class DocumentService {

    /** ขนาดไฟล์สูงสุด 10 MB */
    public static final long MAX_SIZE = 10L * 1024 * 1024;
    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    private final RequestService requestService;
    private final ApplicationRepository applicationRepository;
    private final DocumentRepository documentRepository;
    private final StudentRepository studentRepository;
    private final FileStorage fileStorage;
    private final AccessGuard guard;

    public DocumentService(RequestService requestService, ApplicationRepository applicationRepository,
            DocumentRepository documentRepository, StudentRepository studentRepository, FileStorage fileStorage,
            AccessGuard guard) {
        this.requestService = requestService;
        this.applicationRepository = applicationRepository;
        this.documentRepository = documentRepository;
        this.studentRepository = studentRepository;
        this.fileStorage = fileStorage;
        this.guard = guard;
    }

    /** POST /api/requests/{id}/documents: แนบ Transcript (แทนที่ไฟล์เดิมของคำร้อง ให้เหลือฉบับล่าสุดฉบับเดียว) */
    public DocumentResponse upload(Long applicationId, MultipartFile file, Long userId) {
        return upload(applicationId, file, null, userId);
    }

    /**
     * U5: แนบไฟล์ให้ช่องชนิดไฟล์ที่ระบุ (fieldId) · แทนที่เฉพาะไฟล์เดิมของช่องเดียวกัน
     * fieldId = null คือไฟล์ Transcript แบบเดิม (แทนที่ไฟล์เดิมที่ไม่ได้ระบุช่อง)
     */
    public DocumentResponse upload(Long applicationId, MultipartFile file, Long fieldId, Long userId) {
        Student student = requestService.currentStudent(userId);
        Application app = requestService.ownedApplication(applicationId, student);
        RequestService.requireEditable(app);
        if (fieldId != null) {
            requestService.fileField(app, fieldId);
        }
        byte[] content = readValidPdf(file);
        String fileName = safeName(file.getOriginalFilename());

        List<Document> previous = documentRepository.findByApplicationId(app.getId()).stream()
                .filter(d -> Objects.equals(d.getFormFieldId(), fieldId))
                .toList();
        String key = fileStorage.store(fileName, content); // ล้มเหลว → StorageException → ไม่มี metadata ค้าง
        Document saved;
        try {
            documentRepository.deleteAll(previous);
            saved = documentRepository.saveAndFlush(
                    new Document(app.getId(), fileName, key, (long) content.length, fieldId));
        } catch (RuntimeException e) {
            fileStorage.delete(key);
            throw e;
        }
        previous.forEach(d -> fileStorage.delete(d.getStorageKey()));
        return DocumentResponse.from(saved);
    }

    /** ตรวจไฟล์: ต้องมีไฟล์ · นามสกุล .pdf · ไม่เกิน 10 MB · เนื้อไฟล์เป็น PDF จริง */
    static byte[] readValidPdf(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(400, "INVALID_FILE", "กรุณาเลือกไฟล์ Transcript");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".pdf")) {
            throw new ApiException(400, "INVALID_FILE", "Transcript ต้องเป็นไฟล์ PDF เท่านั้น");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new ApiException(400, "FILE_TOO_LARGE", "ไฟล์ต้องมีขนาดไม่เกิน 10 MB");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new ApiException(400, "INVALID_FILE", "อ่านไฟล์ไม่ได้");
        }
        if (content.length < PDF_MAGIC.length
                || !Arrays.equals(Arrays.copyOf(content, PDF_MAGIC.length), PDF_MAGIC)) {
            throw new ApiException(400, "INVALID_FILE", "ไฟล์นี้ไม่ใช่ PDF ที่ถูกต้อง");
        }
        return content;
    }

    /** GET /api/documents/{id}: เปิดไฟล์ (เจ้าของคำร้อง หรือเจ้าหน้าที่ / อาจารย์) */
    @Transactional(readOnly = true)
    public FileContent download(Long documentId, Long userId) {
        User user = guard.require(userId);
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> ApiException.notFound("ไม่พบไฟล์"));
        if (user.getRole() == Role.STUDENT) {
            Application app = applicationRepository.findById(doc.getApplicationId())
                    .orElseThrow(() -> ApiException.notFound("ไม่พบคำร้อง"));
            Student student = studentRepository.findByUserId(user.getId()).orElseThrow(ApiException::forbidden);
            if (!app.getStudentId().equals(student.getId())) {
                throw ApiException.forbidden();
            }
        }
        return new FileContent(doc.getFileName(), fileStorage.load(doc.getStorageKey()));
    }

    private static String safeName(String original) {
        String name = original == null ? "transcript.pdf" : original.replaceAll("[\\\\/]", "_").trim();
        return name.length() > 200 ? name.substring(name.length() - 200) : name;
    }

    public record FileContent(String fileName, byte[] content) {
    }
}
