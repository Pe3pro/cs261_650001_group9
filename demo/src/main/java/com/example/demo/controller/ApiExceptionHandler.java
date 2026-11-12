package com.example.demo.controller;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.example.demo.dto.ErrorResponse;
import com.example.demo.dto.FieldErrorResponse;
import com.example.demo.entity.Match;
import com.example.demo.service.ApiException;
import com.example.demo.service.FieldValidationException;
import com.example.demo.service.FileStorage;

/** แปลง error เป็น JSON รูปแบบเดียวกันทุก API: {"code": "...", "message": "..."} */
@RestControllerAdvice
public class ApiExceptionHandler {

    /** U3: ข้อมูลในแบบฟอร์มไม่ครบ / ผิดรูปแบบ → 400 พร้อมรายชื่อช่องที่ผิด */
    @ExceptionHandler(FieldValidationException.class)
    public ResponseEntity<FieldErrorResponse> handleFields(FieldValidationException e) {
        return ResponseEntity.badRequest().body(new FieldErrorResponse(e.getCode(), e.getMessage(), e.getFields()));
    }

    /** U3: ไฟล์ใหญ่เกินที่ server รับได้ */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleTooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse("FILE_TOO_LARGE", "ไฟล์ต้องมีขนาดไม่เกิน 10 MB"));
    }

    /** U3: ที่เก็บไฟล์มีปัญหา (ไม่มี metadata ค้างในตาราง documents) */
    @ExceptionHandler(FileStorage.StorageException.class)
    public ResponseEntity<ErrorResponse> handleStorage(FileStorage.StorageException e) {
        return ResponseEntity.status(500).body(
                new ErrorResponse("STORAGE_ERROR", "บันทึกไฟล์ไม่สำเร็จ กรุณาลองใหม่อีกครั้ง"));
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException e) {
        return ResponseEntity.status(e.getStatus()).body(new ErrorResponse(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleBadJson(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse("VALIDATION_ERROR", "รูปแบบข้อมูลไม่ถูกต้อง"));
    }

    /** มีคนบันทึกคำร้องเดียวกันไปก่อนหน้า (ดู @Version ใน Application) */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleConcurrentUpdate(ObjectOptimisticLockingFailureException e) {
        return ResponseEntity.status(409).body(
                new ErrorResponse("INVALID_STATUS", "มีผู้บันทึกผลคำร้องนี้ไปก่อนแล้ว กรุณาโหลดหน้าใหม่"));
    }

    /** U6: ใบสมัครเดียวมีผลจับคู่ที่ใช้อยู่ 2 รายการ (ชน uk_match_active_application) → 409 ALREADY_MATCHED · constraint อื่นส่งต่อตามเดิม */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraint(DataIntegrityViolationException e) {
        String detail = String.valueOf(e.getMostSpecificCause().getMessage());
        if (detail.toLowerCase().contains(Match.UK_ACTIVE_APPLICATION)) {
            return ResponseEntity.status(409).body(
                    new ErrorResponse("ALREADY_MATCHED", "ใบสมัครนี้มีผลการจับคู่อยู่แล้ว"));
        }
        throw e;
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleBadParam(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest().body(
                new ErrorResponse("VALIDATION_ERROR", "ค่า " + e.getName() + " ไม่ถูกต้อง"));
    }
}
