package com.example.demo.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.dto.AutoMatchResponse;
import com.example.demo.dto.CandidateResponse;
import com.example.demo.dto.MatchDecisionRequest;
import com.example.demo.dto.MatchRequest;
import com.example.demo.dto.MatchResponse;
import com.example.demo.dto.MatchSummaryResponse;
import com.example.demo.dto.MatchingRoundRequest;
import com.example.demo.dto.MatchingRoundResponse;
import com.example.demo.entity.MatchStatus;
import com.example.demo.service.MatchingService;

/**
 * U6: การจับคู่นักศึกษากับตำแหน่ง (ดู API Spec ใน .doc/UserStory/U6/T2.md)
 * เจ้าหน้าที่: candidates · auto · matches (สร้าง / ดู / ยกเลิก) · rounds (เปิด / ประกาศผล)
 * นักศึกษา: matches/me · matches/me/summary · matches/{id}/respond
 */
@RestController
public class MatchingController {

    private static final String USER = "X-User-Id";

    private final MatchingService matchingService;

    public MatchingController(MatchingService matchingService) {
        this.matchingService = matchingService;
    }

    @GetMapping("/api/matching/candidates")
    public List<CandidateResponse> getCandidates(@RequestHeader(value = USER, required = false) Long userId,
            @RequestParam(required = false) Long positionId) {
        return matchingService.candidates(userId, positionId);
    }

    @PostMapping("/api/matching/auto")
    public AutoMatchResponse autoMatch(@RequestHeader(value = USER, required = false) Long userId) {
        return matchingService.autoMatch(userId);
    }

    @PostMapping("/api/matches")
    @ResponseStatus(HttpStatus.CREATED)
    public MatchResponse createMatch(@RequestHeader(value = USER, required = false) Long userId,
            @RequestBody(required = false) MatchRequest request) {
        return matchingService.manualMatch(userId, request);
    }

    @GetMapping("/api/matches")
    public List<MatchResponse> getMatches(@RequestHeader(value = USER, required = false) Long userId,
            @RequestParam(required = false) MatchStatus status, @RequestParam(required = false) Long roundId) {
        return matchingService.listMatches(userId, status, roundId);
    }

    @GetMapping("/api/matches/me")
    public MatchResponse getMyMatch(@RequestHeader(value = USER, required = false) Long userId) {
        return matchingService.myMatch(userId);
    }

    @GetMapping("/api/matches/me/summary")
    public MatchSummaryResponse getMySummary(@RequestHeader(value = USER, required = false) Long userId) {
        return matchingService.mySummary(userId);
    }

    @PostMapping("/api/matches/{id}/respond")
    public MatchResponse respond(@RequestHeader(value = USER, required = false) Long userId, @PathVariable Long id,
            @RequestBody(required = false) MatchDecisionRequest request) {
        return matchingService.respond(userId, id, request);
    }

    @DeleteMapping("/api/matches/{id}")
    public MatchResponse cancel(@RequestHeader(value = USER, required = false) Long userId, @PathVariable Long id) {
        return matchingService.cancel(userId, id);
    }

    @GetMapping("/api/matching/rounds/current")
    public MatchingRoundResponse getCurrentRound(@RequestHeader(value = USER, required = false) Long userId) {
        return matchingService.currentRound(userId);
    }

    @PostMapping("/api/matching/rounds")
    @ResponseStatus(HttpStatus.CREATED)
    public MatchingRoundResponse openRound(@RequestHeader(value = USER, required = false) Long userId,
            @RequestBody(required = false) MatchingRoundRequest request) {
        return matchingService.openRound(userId, request);
    }

    @PostMapping("/api/matching/rounds/{id}/announce")
    public MatchingRoundResponse announce(@RequestHeader(value = USER, required = false) Long userId,
            @PathVariable Long id) {
        return matchingService.announce(userId, id);
    }
}
