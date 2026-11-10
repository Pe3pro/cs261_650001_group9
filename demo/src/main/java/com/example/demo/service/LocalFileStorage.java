package com.example.demo.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** U3: เก็บไฟล์ PDF ในโฟลเดอร์ของ server (ตั้งค่าด้วย app.upload-dir) ชื่อไฟล์จริงสุ่มด้วย UUID */
@Component
public class LocalFileStorage implements FileStorage {

    private final Path root;

    public LocalFileStorage(@Value("${app.upload-dir:uploads}") String uploadDir) {
        this.root = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    @Override
    public String store(String fileName, byte[] content) {
        String key = "transcripts/" + UUID.randomUUID() + ".pdf";
        try {
            Path target = resolve(key);
            Files.createDirectories(target.getParent());
            Files.write(target, content);
            return key;
        } catch (IOException e) {
            throw new StorageException("บันทึกไฟล์ไม่สำเร็จ", e);
        }
    }

    /** U8: เก็บไฟล์แนบ (รูป / PDF) ใต้โฟลเดอร์ที่กำหนด ชื่อไฟล์สุ่มด้วย UUID คงนามสกุลเดิมไว้ */
    @Override
    public String store(String folder, String fileName, byte[] content) {
        String safeFolder = folder == null ? "files" : folder.replaceAll("[^A-Za-z0-9/_-]", "");
        String ext = "";
        int dot = fileName == null ? -1 : fileName.lastIndexOf('.');
        if (dot >= 0) {
            ext = fileName.substring(dot).toLowerCase(java.util.Locale.ROOT).replaceAll("[^.a-z0-9]", "");
        }
        String key = safeFolder + "/" + UUID.randomUUID() + ext;
        try {
            Path target = resolve(key);
            Files.createDirectories(target.getParent());
            Files.write(target, content);
            return key;
        } catch (IOException e) {
            throw new StorageException("บันทึกไฟล์ไม่สำเร็จ", e);
        }
    }

    @Override
    public byte[] load(String storageKey) {
        try {
            return Files.readAllBytes(resolve(storageKey));
        } catch (IOException e) {
            throw new StorageException("ไม่พบไฟล์", e);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (IOException e) {
            // ลบไม่ได้ก็ไม่กระทบข้อมูลในฐานข้อมูล
        }
    }

    /** กัน path traversal: storageKey ต้องอยู่ใต้โฟลเดอร์ upload เท่านั้น */
    private Path resolve(String storageKey) {
        Path p = root.resolve(storageKey).normalize();
        if (!p.startsWith(root)) {
            throw new StorageException("storageKey ไม่ถูกต้อง", null);
        }
        return p;
    }
}
