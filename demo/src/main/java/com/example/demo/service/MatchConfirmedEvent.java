package com.example.demo.service;

/**
 * U6: เกิดเมื่อนักศึกษายืนยันสิทธิ์ (match เป็น CONFIRMED) ใน MatchingService.respond (T2 ข้อ S8)
 * ส่งแบบ synchronous ใน transaction เดียวกัน · US7 รับด้วย @EventListener แล้วเรียก InternshipService.createFromMatch
 * ถ้าผู้รับ throw exception การยืนยันสิทธิ์ rollback ด้วย
 */
public record MatchConfirmedEvent(Long matchId, Long applicationId, Long positionId, Long roundId) {
}
