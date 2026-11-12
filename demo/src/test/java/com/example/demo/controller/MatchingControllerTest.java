package com.example.demo.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.demo.dto.AutoMatchResponse;
import com.example.demo.dto.MatchDecisionRequest;
import com.example.demo.dto.MatchRequest;
import com.example.demo.dto.MatchResponse;
import com.example.demo.dto.MatchingRoundRequest;
import com.example.demo.dto.MatchingRoundResponse;
import com.example.demo.dto.UnmatchedItem;
import com.example.demo.entity.MatchStatus;
import com.example.demo.entity.RoundStatus;
import com.example.demo.service.ApiException;
import com.example.demo.service.MatchingService;

/** U6:T4 Unit test ส่วน API: การจับคู่ รอบ และการตอบรับ (MatchingController) */
@WebMvcTest(MatchingController.class)
class MatchingControllerTest {

    private static final LocalDateTime DEADLINE = LocalDateTime.of(2026, 11, 20, 23, 59);

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private MatchingService matchingService;

    private static MatchResponse match(MatchStatus status) {
        return new MatchResponse(70L, 40L, "6609650012", "พิมพ์ชนก ตั้งใจ", 11L, "Backend Developer", "SCB TechX", 1,
                status, 5L, 1, LocalDateTime.of(2026, 11, 10, 10, 0), null, DEADLINE, null);
    }

    private static MatchingRoundResponse round(int no, RoundStatus status) {
        return new MatchingRoundResponse(5L, no, status, LocalDateTime.of(2026, 11, 10, 9, 0), null, DEADLINE, 0);
    }

    @Test
    @DisplayName("U6-API-M01 จับคู่อัตโนมัติ ได้ 200 พร้อมรายการที่จับได้และจับไม่ได้")
    void autoMatch() throws Exception {
        when(matchingService.autoMatch(1L)).thenReturn(new AutoMatchResponse(1, List.of(match(MatchStatus.PROPOSED)),
                List.of(new UnmatchedItem(41L, "ธนกร ขยันดี", "ตำแหน่งที่เลือกเต็มหรือปิดรับทั้งหมด"))));

        mvc.perform(post("/api/matching/auto").header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roundNo").value(1))
                .andExpect(jsonPath("$.matched", hasSize(1)))
                .andExpect(jsonPath("$.matched[0].status").value("PROPOSED"))
                .andExpect(jsonPath("$.unmatched[0].reason").value("ตำแหน่งที่เลือกเต็มหรือปิดรับทั้งหมด"));
    }

    @Test
    @DisplayName("U6-API-M02 ยังไม่เปิดรอบ จับคู่อัตโนมัติได้ 409 NO_OPEN_ROUND")
    void autoMatchNoRound() throws Exception {
        when(matchingService.autoMatch(1L)).thenThrow(new ApiException(409, "NO_OPEN_ROUND", "ยังไม่ได้เปิดรอบการจับคู่"));

        mvc.perform(post("/api/matching/auto").header("X-User-Id", "1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NO_OPEN_ROUND"));
    }

    @Test
    @DisplayName("U6-API-M03 จับคู่เอง ได้ 201")
    void manualMatch() throws Exception {
        when(matchingService.manualMatch(eq(1L), eq(new MatchRequest(40L, 11L)))).thenReturn(match(MatchStatus.PROPOSED));

        mvc.perform(post("/api/matches").header("X-User-Id", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"applicationId\": 40, \"positionId\": 11}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(70))
                .andExpect(jsonPath("$.positionName").value("Backend Developer"));
    }

    @Test
    @DisplayName("U6-API-M04 ดูผลจับคู่กรองตามรอบ ส่ง status และ roundId ไปที่ service")
    void listByRound() throws Exception {
        when(matchingService.listMatches(1L, MatchStatus.PROPOSED, 5L)).thenReturn(List.of(match(MatchStatus.PROPOSED)));

        mvc.perform(get("/api/matches").param("status", "PROPOSED").param("roundId", "5").header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    @DisplayName("U6-API-M05 ยกเลิกผล ได้ 200 สถานะ CANCELLED")
    void cancel() throws Exception {
        when(matchingService.cancel(1L, 70L)).thenReturn(match(MatchStatus.CANCELLED));

        mvc.perform(delete("/api/matches/70").header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("U6-API-R01 เปิดรอบใหม่ ได้ 201")
    void openRound() throws Exception {
        when(matchingService.openRound(eq(1L), eq(new MatchingRoundRequest(DEADLINE)))).thenReturn(round(2, RoundStatus.OPEN));

        mvc.perform(post("/api/matching/rounds").header("X-User-Id", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmDeadline\": \"2026-11-20T23:59:00\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roundNo").value(2))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    @DisplayName("U6-API-R02 รอบเดิมยังไม่จบ เปิดรอบใหม่ได้ 409 ROUND_NOT_CLOSED")
    void openRoundNotClosed() throws Exception {
        when(matchingService.openRound(eq(1L), any()))
                .thenThrow(new ApiException(409, "ROUND_NOT_CLOSED", "รอบที่ 1 ยังไม่จบ"));

        mvc.perform(post("/api/matching/rounds").header("X-User-Id", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmDeadline\": \"2026-11-20T23:59:00\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROUND_NOT_CLOSED"));
    }

    @Test
    @DisplayName("U6-API-R03 ประกาศผล ได้ 200 สถานะ ANNOUNCED")
    void announce() throws Exception {
        when(matchingService.announce(1L, 5L)).thenReturn(round(1, RoundStatus.ANNOUNCED));

        mvc.perform(post("/api/matching/rounds/5/announce").header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ANNOUNCED"));
    }

    @Test
    @DisplayName("U6-API-R04 ยังไม่มีรอบ ดูรอบปัจจุบันได้ 404")
    void currentRoundNotFound() throws Exception {
        when(matchingService.currentRound(1L)).thenThrow(ApiException.notFound("ยังไม่มีรอบการจับคู่"));

        mvc.perform(get("/api/matching/rounds/current").header("X-User-Id", "1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("U6-API-S01 นักศึกษาสละสิทธิ์ ส่ง accept=false และเหตุผลไปที่ service")
    void respondDecline() throws Exception {
        when(matchingService.respond(eq(9L), eq(70L), eq(new MatchDecisionRequest(false, "ได้ที่ฝึกงานอื่นแล้ว"))))
                .thenReturn(match(MatchStatus.DECLINED));

        mvc.perform(post("/api/matches/70/respond").header("X-User-Id", "9")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accept\": false, \"reason\": \"ได้ที่ฝึกงานอื่นแล้ว\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DECLINED"));
    }

    @Test
    @DisplayName("U6-API-S02 ตอบเลยกำหนด ได้ 409 DEADLINE_PASSED")
    void respondLate() throws Exception {
        when(matchingService.respond(eq(9L), eq(70L), any()))
                .thenThrow(new ApiException(409, "DEADLINE_PASSED", "เลยกำหนดยืนยันสิทธิ์แล้ว"));

        mvc.perform(post("/api/matches/70/respond").header("X-User-Id", "9")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accept\": true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEADLINE_PASSED"));
    }

    @Test
    @DisplayName("U6-API-S03 ยังไม่ประกาศผล ดูหน้าสรุปได้ 404")
    void summaryNotAnnounced() throws Exception {
        when(matchingService.mySummary(9L)).thenThrow(ApiException.notFound("ยังไม่ประกาศผลการจับคู่"));

        mvc.perform(get("/api/matches/me/summary").header("X-User-Id", "9"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("U6-API-S04 ไม่ส่ง X-User-Id ได้ 401 UNAUTHORIZED")
    void noUser() throws Exception {
        when(matchingService.myMatch(isNull())).thenThrow(ApiException.unauthorized());

        mvc.perform(get("/api/matches/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
