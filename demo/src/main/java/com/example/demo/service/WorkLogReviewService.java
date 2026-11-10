package com.example.demo.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.dao.UserRepository;
import com.example.demo.dao.WorkLogDao;
import com.example.demo.dto.BulkWorkLogReviewResponse;
import com.example.demo.dto.BulkWorkLogReviewResponse.SkippedItem;
import com.example.demo.entity.ReviewDecision;
import com.example.demo.entity.Role;
import com.example.demo.entity.User;
import com.example.demo.entity.WorkLog;
import com.example.demo.entity.WorkLogStatus;

/** U8: อาจารย์ที่ปรึกษาตรวจบันทึกการทำงาน ตาม Sequence Diagram "อาจารย์ตรวจบันทึกการทำงาน" */
@Service
@Transactional
public class WorkLogReviewService {

    public static final int MAX_BULK = 100;

    private final WorkLogDao workLogDao;
    private final UserRepository userDao;
    private final NotificationService notificationService;
    private final AccessGuard guard;
    private final Clock clock;

    public WorkLogReviewService(WorkLogDao workLogDao, UserRepository userDao,
            NotificationService notificationService, AccessGuard guard, Clock clock) {
        this.workLogDao = workLogDao;
        this.userDao = userDao;
        this.notificationService = notificationService;
        this.guard = guard;
        this.clock = clock;
    }

    /** GET /api/work-logs/pending-review: บันทึกที่รอตรวจของนักศึกษาในที่ปรึกษา */
    @Transactional(readOnly = true)
    public List<WorkLog> getPending(Long advisorId) {
        User advisor = guard.require(advisorId, Role.ADVISOR);
        return workLogDao.findSubmittedByAdvisorId(advisor.getId());
    }

    /** POST /api/work-logs/{id}/review: อนุมัติ / ส่งกลับแก้ไข (ส่งกลับต้องมีความเห็น) แล้วแจ้งนักศึกษา */
    public WorkLog review(Long advisorId, Long id, ReviewDecision decision, String comment) {
        User advisor = guard.require(advisorId, Role.ADVISOR);
        WorkLog log = workLogDao.findById(id).orElseThrow(() -> ApiException.notFound("ไม่พบบันทึกการทำงาน"));
        checkAdvisor(advisor, log);
        if (log.getStatus() != WorkLogStatus.SUBMITTED) {
            throw new ApiException(409, "WORK_LOG_NOT_PENDING", "บันทึกนี้ไม่ได้อยู่ในสถานะรอตรวจ");
        }
        if (decision == null) {
            throw ApiException.badRequest("กรุณาเลือกผลการตรวจ (APPROVE / REVISE)");
        }
        if (decision == ReviewDecision.REVISE && (comment == null || comment.isBlank())) {
            throw new ApiException(400, "COMMENT_REQUIRED", "กรุณากรอกความเห็นเมื่อส่งกลับแก้ไข");
        }
        log.review(decision, advisor.getId(), comment, LocalDateTime.now(clock));
        WorkLog saved = workLogDao.save(log);
        notificationService.notifyWorkLogReviewed(saved);
        return saved;
    }

    /** POST /api/work-logs/review-bulk: อนุมัติทีละบันทึก (ไม่เกิน 100) บันทึกที่ไม่ผ่านเงื่อนไขใส่ใน skipped พร้อมเหตุผล */
    public BulkWorkLogReviewResponse reviewBulk(Long advisorId, List<Long> ids, String comment) {
        User advisor = guard.require(advisorId, Role.ADVISOR);
        if (ids == null || ids.isEmpty()) {
            throw ApiException.badRequest("กรุณาเลือกบันทึกที่จะอนุมัติ");
        }
        List<Long> unique = ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (unique.size() > MAX_BULK) {
            throw ApiException.badRequest("อนุมัติได้ครั้งละไม่เกิน " + MAX_BULK + " บันทึก");
        }
        Map<Long, WorkLog> found = workLogDao.findAllByIdIn(unique).stream()
                .collect(Collectors.toMap(WorkLog::getId, Function.identity()));
        LocalDateTime now = LocalDateTime.now(clock);
        List<Long> approved = new ArrayList<>();
        List<SkippedItem> skipped = new ArrayList<>();
        for (Long id : unique) {
            WorkLog log = found.get(id);
            if (log == null) {
                skipped.add(new SkippedItem(id, "ไม่พบบันทึก"));
            } else if (!isAdvisorOf(advisor, log)) {
                skipped.add(new SkippedItem(id, "ไม่ใช่นักศึกษาในที่ปรึกษา"));
            } else if (log.getStatus() != WorkLogStatus.SUBMITTED) {
                skipped.add(new SkippedItem(id, "บันทึกไม่ได้อยู่ในสถานะรอตรวจ"));
            } else {
                log.review(ReviewDecision.APPROVE, advisor.getId(), comment, now);
                notificationService.notifyWorkLogReviewed(workLogDao.save(log));
                approved.add(id);
            }
        }
        return new BulkWorkLogReviewResponse(approved, skipped);
    }

    /** ต้องเป็นอาจารย์ที่ปรึกษาของนักศึกษาเจ้าของบันทึก ไม่ใช่ → 403 */
    void checkAdvisor(User advisor, WorkLog log) {
        if (!isAdvisorOf(advisor, log)) {
            throw ApiException.forbidden();
        }
    }

    private boolean isAdvisorOf(User advisor, WorkLog log) {
        return userDao.findById(log.getStudentId())
                .map(s -> advisor.getId().equals(s.getAdvisorId()))
                .orElse(false);
    }
}
