package com.example.demo.controller;

import com.example.demo.dto.EligibilityRequest;
import com.example.demo.dto.EligibilityResponse;
import com.example.demo.service.EligibilityService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/eligibility")
@CrossOrigin(origins = "*")
public class EligibilityController {

    private final EligibilityService eligibilityService;

    public EligibilityController(
        EligibilityService eligibilityService
    ) {
        this.eligibilityService = eligibilityService;
    }

    @PostMapping("/check")
    public ResponseEntity<EligibilityResponse> check(
        @RequestHeader(
            value = "X-User-Id",
            required = false
        )
        Long userId,

        @RequestBody(required = false)
        EligibilityRequest request
    ) {
        EligibilityResponse response =
            eligibilityService.check(
                userId,
                request
            );

        HttpStatus status = response.valid()
            ? HttpStatus.OK
            : HttpStatus.BAD_REQUEST;

        return ResponseEntity
            .status(status)
            .body(response);
    }
}