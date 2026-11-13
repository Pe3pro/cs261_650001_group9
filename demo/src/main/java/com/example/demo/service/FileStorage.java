package com.example.demo.service;

/**
 * U3: ที่เก็บไฟล์ PDF (Transcript) แยกเป็น interface เพื่อให้ test จำลองได้
 * ตาม Sequence Diagram: Controller → PDF Storage: Store PDF
 */
public interface FileStorage {

    /** บันทึกไฟล์ คืน storageKey สำหรับอ้างอิงภายหลัง · บันทึกไม่ได้ให้ throw StorageException */
    String store(String fileName, byte[] content);

    /**
     * U8: บันทึกไฟล์ลงโฟลเดอร์ที่กำหนด (เช่น work-logs/{id}) คืน storageKey
     * ค่าเริ่มต้นใช้ store(fileName, content) เดิม ให้คลาสที่ implement เดิมยังใช้ได้
     */
    default String store(String folder, String fileName, byte[] content) {
        return store(fileName, content);
    }

    /** อ่านไฟล์ตาม storageKey */
    byte[] load(String storageKey);

    /** ลบไฟล์ (ใช้ตอน rollback เมื่อบันทึก metadata ไม่สำเร็จ) */
    void delete(String storageKey);

    /** ที่เก็บไฟล์มีปัญหา → API ตอบ 500 STORAGE_ERROR */
    class StorageException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public StorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
