package com.example.demo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.demo.dao.FormFieldRepository;
import com.example.demo.dao.FormTemplateRepository;
import com.example.demo.dto.FormFieldRequest;
import com.example.demo.dto.FormTemplateRequest;
import com.example.demo.dto.FormTemplateResponse;
import com.example.demo.entity.FieldType;
import com.example.demo.entity.FormField;
import com.example.demo.entity.FormStatus;
import com.example.demo.entity.FormTemplate;
import com.example.demo.entity.Role;
import com.example.demo.entity.User;

/** U5:T8 Unit test logic การสร้างแบบฟอร์ม (FormTemplateService) โดยไม่ต่อฐานข้อมูล */
class FormTemplateServiceTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-10-17T03:00:00Z"), ZoneId.of("Asia/Bangkok")); // 10:00 น.
    private static final LocalDateTime DEADLINE = LocalDateTime.of(2026, 11, 30, 23, 59, 59);

    private FormTemplateRepository templates;
    private FormFieldRepository fields;
    private FormTemplateService service;

    @BeforeEach
    void setUp() {
        templates = mock(FormTemplateRepository.class);
        fields = mock(FormFieldRepository.class);
        AccessGuard guard = mock(AccessGuard.class);
        User staff = new User("staff01", "สมศรี ทำงานดี", null, Role.STAFF);
        ReflectionTestUtils.setField(staff, "id", 1L);
        when(guard.require(1L, Role.STAFF, Role.ADMIN)).thenReturn(staff);
        when(guard.require(3L, Role.STAFF, Role.ADMIN)).thenThrow(ApiException.forbidden());
        service = new FormTemplateService(templates, fields, guard, CLOCK);
        when(templates.save(any(FormTemplate.class))).thenAnswer(inv -> {
            FormTemplate t = inv.getArgument(0);
            if (t.getId() == null) ReflectionTestUtils.setField(t, "id", 10L);
            return t;
        });
    }

    private static FormTemplateRequest request(String name, List<FormFieldRequest> f) {
        return new FormTemplateRequest(name, "แนบ Transcript", DEADLINE, f);
    }

    private static List<FormFieldRequest> twoFields() {
        return List.of(new FormFieldRequest("ชื่อ-นามสกุล", FieldType.TEXT, true, null),
                new FormFieldRequest("เบอร์โทรศัพท์", FieldType.PHONE, false, null));
    }

    private FormTemplate draft() {
        FormTemplate t = new FormTemplate("ร่าง", 2, 1L);
        ReflectionTestUtils.setField(t, "id", 10L);
        t.setDeadline(DEADLINE);
        when(templates.findById(10L)).thenReturn(Optional.of(t));
        return t;
    }

    @Test
    @DisplayName("SV-F01 สร้างแบบฟอร์มฉบับแรก ได้ version 1 สถานะ DRAFT และบันทึกช่องตามลำดับ")
    void createFirst() {
        when(templates.findTopByOrderByVersionDesc()).thenReturn(Optional.empty());

        FormTemplateResponse res = service.create(request("  คำร้องสหกิจ 2/2569 ", twoFields()), 1L);

        assertThat(res.version()).isEqualTo(1);
        assertThat(res.status()).isEqualTo(FormStatus.DRAFT);
        assertThat(res.name()).isEqualTo("คำร้องสหกิจ 2/2569");
        verify(fields, times(2)).save(any(FormField.class));
    }

    @Test
    @DisplayName("SV-F02 สร้างฉบับใหม่ต่อจากฉบับล่าสุด version เพิ่มขึ้น 1")
    void createNextVersion() {
        when(templates.findTopByOrderByVersionDesc()).thenReturn(Optional.of(new FormTemplate("เก่า", 4, 1L)));

        assertThat(service.create(request("ใหม่", twoFields()), 1L).version()).isEqualTo(5);
    }

    @Test
    @DisplayName("SV-F03 ไม่มีชื่อแบบฟอร์ม ได้ 400")
    void noName() {
        assertThatThrownBy(() -> service.create(request("  ", twoFields()), 1L))
                .isInstanceOf(ApiException.class).hasMessage("กรุณาระบุชื่อแบบฟอร์ม");
    }

    @Test
    @DisplayName("SV-F04 ไม่มีช่องเลย ได้ 400")
    void noFields() {
        assertThatThrownBy(() -> service.create(request("ฟอร์ม", List.of()), 1L))
                .isInstanceOf(ApiException.class).hasMessage("แบบฟอร์มต้องมีอย่างน้อย 1 ช่อง");
    }

    @Test
    @DisplayName("SV-F05 ช่องที่ 2 ไม่มีชื่อ ได้ 400 บอกเลขช่อง")
    void fieldWithoutLabel() {
        List<FormFieldRequest> f = List.of(new FormFieldRequest("ชื่อ", FieldType.TEXT, true, null),
                new FormFieldRequest("", FieldType.TEXT, true, null));

        assertThatThrownBy(() -> service.create(request("ฟอร์ม", f), 1L))
                .isInstanceOf(ApiException.class).hasMessage("ช่องที่ 2 ยังไม่มีชื่อช่อง");
    }

    @Test
    @DisplayName("SV-F06 ช่องตัวเลือกแต่ไม่ใส่ตัวเลือก ได้ 400")
    void selectWithoutOptions() {
        List<FormFieldRequest> f = List.of(new FormFieldRequest("ภาคการศึกษา", FieldType.SELECT, true, " "));

        assertThatThrownBy(() -> service.create(request("ฟอร์ม", f), 1L))
                .isInstanceOf(ApiException.class).extracting("status").isEqualTo(400);
    }

    @Test
    @DisplayName("SV-F07 นักศึกษาสร้างแบบฟอร์ม ได้ 403")
    void studentCannotCreate() {
        assertThatThrownBy(() -> service.create(request("ฟอร์ม", twoFields()), 3L))
                .isInstanceOf(ApiException.class).extracting("status").isEqualTo(403);
        verify(templates, never()).save(any());
    }

    @Test
    @DisplayName("SV-F08 แก้ไขฉบับร่าง ลบช่องเดิมแล้วบันทึกชุดใหม่")
    void updateDraft() {
        draft();

        service.update(10L, request("ชื่อใหม่", twoFields()), 1L);

        verify(fields).deleteByFormTemplateId(10L);
        verify(fields, times(2)).save(any(FormField.class));
    }

    @Test
    @DisplayName("SV-F09 แก้ไขฉบับที่เปิดใช้งานแล้ว ได้ 409")
    void updatePublished() {
        FormTemplate t = draft();
        t.publish(LocalDateTime.of(2026, 10, 1, 9, 0));

        assertThatThrownBy(() -> service.update(10L, request("x", twoFields()), 1L))
                .isInstanceOf(ApiException.class).extracting("status").isEqualTo(409);
        verify(fields, never()).deleteByFormTemplateId(any());
    }

    @Test
    @DisplayName("SV-F10 เปิดใช้งานฉบับใหม่ ฉบับเดิมที่เปิดอยู่ถูกเลิกใช้ (ARCHIVED)")
    void publishArchivesOld() {
        FormTemplate t = draft();
        FormTemplate old = new FormTemplate("เก่า", 1, 1L);
        old.publish(LocalDateTime.of(2026, 6, 1, 9, 0));
        when(fields.countByFormTemplateId(10L)).thenReturn(2L);
        when(templates.findByStatus(FormStatus.PUBLISHED)).thenReturn(List.of(old));

        FormTemplateResponse res = service.publish(10L, 1L);

        assertThat(res.status()).isEqualTo(FormStatus.PUBLISHED);
        assertThat(t.getPublishedAt()).isEqualTo(LocalDateTime.of(2026, 10, 17, 10, 0));
        assertThat(old.getStatus()).isEqualTo(FormStatus.ARCHIVED);
    }

    @Test
    @DisplayName("SV-F11 เปิดใช้งานโดยวันหมดเขตผ่านไปแล้ว ได้ 400")
    void publishWithPastDeadline() {
        FormTemplate t = draft();
        t.setDeadline(LocalDateTime.of(2026, 10, 1, 23, 59));
        when(fields.countByFormTemplateId(10L)).thenReturn(2L);

        assertThatThrownBy(() -> service.publish(10L, 1L))
                .isInstanceOf(ApiException.class).extracting("status").isEqualTo(400);
        assertThat(t.getStatus()).isEqualTo(FormStatus.DRAFT);
    }

    @Test
    @DisplayName("SV-F12 แบบฟอร์มที่ไม่มีอยู่ ได้ 404")
    void notFound() {
        when(templates.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(999L, 1L))
                .isInstanceOf(ApiException.class).extracting("status").isEqualTo(404);
    }

    @Test
    @DisplayName("SV-F13 ชื่อช่องซ้ำกันในแบบฟอร์มเดียวกัน (ไม่สนช่องว่าง/ตัวพิมพ์) ได้ 400")
    void duplicateLabel() {
        List<FormFieldRequest> f = List.of(new FormFieldRequest("อีเมล", FieldType.EMAIL, true, null),
                new FormFieldRequest(" อีเมล ", FieldType.TEXT, false, null));

        assertThatThrownBy(() -> service.create(request("ฟอร์ม", f), 1L))
                .isInstanceOf(ApiException.class).hasMessageContaining("ซ้ำ");
    }

    @Test
    @DisplayName("SV-F14 สร้างฉบับใหม่จากฉบับที่เปิดใช้งาน ได้ DRAFT version ถัดไป คัดลอกช่องเดิม ฉบับเดิมไม่เปลี่ยน")
    void newVersionCopiesFields() {
        FormTemplate published = draft();
        published.publish(LocalDateTime.of(2026, 10, 1, 9, 0));
        when(templates.findTopByOrderByVersionDesc()).thenReturn(Optional.of(published));
        when(fields.findByFormTemplateIdOrderByDisplayOrderAsc(10L)).thenReturn(List.of(
                new FormField(10L, "ชื่อ-นามสกุล", FieldType.TEXT, true, 1),
                new FormField(10L, "อีเมล", FieldType.EMAIL, true, 2)));
        when(templates.save(any(FormTemplate.class))).thenAnswer(inv -> {
            FormTemplate t = inv.getArgument(0);
            if (t.getId() == null) ReflectionTestUtils.setField(t, "id", 11L);
            return t;
        });

        FormTemplateResponse res = service.newVersion(10L, 1L);

        assertThat(res.status()).isEqualTo(FormStatus.DRAFT);
        assertThat(res.version()).isEqualTo(3);
        assertThat(published.getStatus()).isEqualTo(FormStatus.PUBLISHED);
        verify(fields, times(2)).save(any(FormField.class));
    }

    @Test
    @DisplayName("SV-F15 ปิดรับคำร้องก่อนกำหนด: วันหมดเขตเลื่อนมาเป็นเวลานี้ สถานะยังเป็น PUBLISHED")
    void closeEarly() {
        FormTemplate t = draft();
        t.publish(LocalDateTime.of(2026, 10, 1, 9, 0));

        FormTemplateResponse res = service.close(10L, 1L);

        assertThat(res.deadline()).isEqualTo(LocalDateTime.of(2026, 10, 17, 10, 0));
        assertThat(res.status()).isEqualTo(FormStatus.PUBLISHED);
    }

    @Test
    @DisplayName("SV-F16 ปิดรับแบบฟอร์มที่ยังเป็นร่าง / หมดเขตไปแล้ว: 409 · นักศึกษาปิดรับ: 403")
    void closeRejected() {
        FormTemplate t = draft();
        assertThatThrownBy(() -> service.close(10L, 1L))
                .isInstanceOf(ApiException.class).extracting("status").isEqualTo(409);

        t.publish(LocalDateTime.of(2026, 10, 1, 9, 0));
        t.setDeadline(LocalDateTime.of(2026, 10, 16, 23, 59));
        assertThatThrownBy(() -> service.close(10L, 1L))
                .isInstanceOf(ApiException.class).extracting("status").isEqualTo(409);

        assertThatThrownBy(() -> service.close(10L, 3L))
                .isInstanceOf(ApiException.class).extracting("status").isEqualTo(403);
    }
}

