package com.example.demo.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.dao.FormFieldRepository;
import com.example.demo.dao.FormTemplateRepository;
import com.example.demo.dto.FormFieldRequest;
import com.example.demo.dto.FormFieldResponse;
import com.example.demo.dto.FormTemplateRequest;
import com.example.demo.dto.FormTemplateResponse;
import com.example.demo.entity.FieldType;
import com.example.demo.entity.FormField;
import com.example.demo.entity.FormStatus;
import com.example.demo.entity.FormTemplate;
import com.example.demo.entity.Role;

/** U5: เจ้าหน้าที่สร้าง แก้ไข และเปิดใช้งานแบบฟอร์มคำร้อง */
@Service
@Transactional
public class FormTemplateService {

    private final FormTemplateRepository templateRepository;
    private final FormFieldRepository fieldRepository;
    private final AccessGuard guard;
    private final Clock clock;

    public FormTemplateService(FormTemplateRepository templateRepository, FormFieldRepository fieldRepository,
            AccessGuard guard, Clock clock) {
        this.templateRepository = templateRepository;
        this.fieldRepository = fieldRepository;
        this.guard = guard;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<FormTemplateResponse> getAll(Long userId) {
        guard.require(userId, Role.STAFF, Role.ADMIN);
        return templateRepository.findAllByOrderByVersionDesc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public FormTemplateResponse getById(Long id, Long userId) {
        guard.require(userId, Role.STAFF, Role.ADMIN);
        return toResponse(findTemplate(id));
    }

    public FormTemplateResponse create(FormTemplateRequest request, Long userId) {
        Long staffId = guard.require(userId, Role.STAFF, Role.ADMIN).getId();
        validate(request);
        int nextVersion = templateRepository.findTopByOrderByVersionDesc()
                .map(t -> t.getVersion() + 1).orElse(1);
        FormTemplate template = new FormTemplate(request.name().trim(), nextVersion, staffId);
        template.setDescription(request.description());
        template.setDeadline(request.deadline());
        template = templateRepository.save(template);
        saveFields(template.getId(), request.fields());
        return toResponse(template);
    }

    public FormTemplateResponse update(Long id, FormTemplateRequest request, Long userId) {
        guard.require(userId, Role.STAFF, Role.ADMIN);
        FormTemplate template = findTemplate(id);
        if (!template.isDraft()) {
            throw ApiException.conflict("แก้ไขได้เฉพาะแบบฟอร์มที่ยังเป็นฉบับร่าง");
        }
        validate(request);
        template.setName(request.name().trim());
        template.setDescription(request.description());
        template.setDeadline(request.deadline());
        fieldRepository.deleteByFormTemplateId(id);
        fieldRepository.flush();
        saveFields(id, request.fields());
        return toResponse(templateRepository.save(template));
    }

    /**
     * สร้างฉบับร่างใหม่จากแบบฟอร์มเดิม (ใช้เมื่อต้องแก้แบบฟอร์มที่เปิดใช้งานแล้ว)
     * คำร้องที่ยื่นไปแล้วยังอ้างฉบับเดิม เพราะฉบับเดิมไม่ถูกแก้ไข
     */
    public FormTemplateResponse newVersion(Long sourceId, Long userId) {
        Long staffId = guard.require(userId, Role.STAFF, Role.ADMIN).getId();
        FormTemplate source = findTemplate(sourceId);
        int nextVersion = templateRepository.findTopByOrderByVersionDesc()
                .map(t -> t.getVersion() + 1).orElse(1);
        FormTemplate copy = new FormTemplate(source.getName(), nextVersion, staffId);
        copy.setDescription(source.getDescription());
        copy = templateRepository.save(copy);
        for (FormField f : fieldRepository.findByFormTemplateIdOrderByDisplayOrderAsc(sourceId)) {
            FormField field = new FormField(copy.getId(), f.getLabel(), f.getFieldType(), f.getRequired(),
                    f.getDisplayOrder());
            field.setOptions(f.getOptions());
            fieldRepository.save(field);
        }
        return toResponse(copy);
    }

    /** เปิดใช้งานแบบฟอร์ม: ฉบับที่เปิดอยู่เดิมจะถูกเลิกใช้ ให้มีฉบับที่นักศึกษาใช้ยื่นได้ทีละ 1 ฉบับ */
    public FormTemplateResponse publish(Long id, Long userId) {
        guard.require(userId, Role.STAFF, Role.ADMIN);
        FormTemplate template = findTemplate(id);
        if (!template.isDraft()) {
            throw ApiException.conflict("แบบฟอร์มนี้เปิดใช้งานหรือเลิกใช้ไปแล้ว");
        }
        if (fieldRepository.countByFormTemplateId(id) == 0) {
            throw ApiException.badRequest("แบบฟอร์มต้องมีอย่างน้อย 1 ช่องก่อนเปิดใช้งาน");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (template.getDeadline() == null || !template.getDeadline().isAfter(now)) {
            throw ApiException.badRequest("กรุณากำหนดวันหมดเขตที่ยังไม่ผ่านไป ก่อนเปิดใช้งาน");
        }
        for (FormTemplate old : templateRepository.findByStatus(FormStatus.PUBLISHED)) {
            old.archive();
            templateRepository.save(old);
        }
        template.publish(now);
        return toResponse(templateRepository.save(template));
    }

    /**
     * U5: ปิดรับคำร้องก่อนวันหมดเขต · เลื่อนวันหมดเขตมาเป็นเวลานี้
     * ผลเหมือนหมดเขตตามปกติ: นักศึกษายื่นใหม่ / ส่งฉบับแก้ไม่ได้อีก แต่คำร้องที่ส่งแล้วยังพิจารณาต่อได้
     */
    public FormTemplateResponse close(Long id, Long userId) {
        guard.require(userId, Role.STAFF, Role.ADMIN);
        FormTemplate template = findTemplate(id);
        if (template.getStatus() != FormStatus.PUBLISHED) {
            throw ApiException.conflict("ปิดรับได้เฉพาะแบบฟอร์มที่เปิดใช้งานอยู่");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (template.getDeadline() != null && !template.getDeadline().isAfter(now)) {
            throw ApiException.conflict("แบบฟอร์มนี้หมดเขตรับคำร้องไปแล้ว");
        }
        template.setDeadline(now);
        return toResponse(templateRepository.save(template));
    }

    private FormTemplate findTemplate(Long id) {
        return templateRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("ไม่พบแบบฟอร์ม"));
    }

    private void validate(FormTemplateRequest request) {
        if (request == null || isBlank(request.name())) {
            throw ApiException.badRequest("กรุณาระบุชื่อแบบฟอร์ม");
        }
        if (request.fields() == null || request.fields().isEmpty()) {
            throw ApiException.badRequest("แบบฟอร์มต้องมีอย่างน้อย 1 ช่อง");
        }
        Set<String> labels = new HashSet<>();
        for (int i = 0; i < request.fields().size(); i++) {
            FormFieldRequest f = request.fields().get(i);
            int no = i + 1;
            if (f == null || isBlank(f.label())) {
                throw ApiException.badRequest("ช่องที่ " + no + " ยังไม่มีชื่อช่อง");
            }
            if (!labels.add(f.label().trim().toLowerCase(Locale.ROOT))) {
                throw ApiException.badRequest("ชื่อช่อง \"" + f.label().trim() + "\" ซ้ำกับช่องอื่น");
            }
            if (f.fieldType() == null) {
                throw ApiException.badRequest("ช่องที่ " + no + " ยังไม่ได้เลือกชนิดของช่อง");
            }
            if (f.fieldType() == FieldType.SELECT && isBlank(f.options())) {
                throw ApiException.badRequest("ช่องที่ " + no + " เป็นตัวเลือก ต้องมีตัวเลือกอย่างน้อย 1 รายการ");
            }
        }
    }

    private void saveFields(Long templateId, List<FormFieldRequest> fields) {
        for (int i = 0; i < fields.size(); i++) {
            FormFieldRequest f = fields.get(i);
            FormField field = new FormField(templateId, f.label().trim(), f.fieldType(),
                    Boolean.TRUE.equals(f.required()), i + 1);
            if (f.fieldType() == FieldType.SELECT) {
                field.setOptions(f.options().trim());
            }
            fieldRepository.save(field);
        }
    }

    private FormTemplateResponse toResponse(FormTemplate t) {
        List<FormFieldResponse> fields = fieldRepository.findByFormTemplateIdOrderByDisplayOrderAsc(t.getId())
                .stream().map(FormFieldResponse::from).toList();
        return FormTemplateResponse.from(t, fields);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
