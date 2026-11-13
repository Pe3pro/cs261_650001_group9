package com.example.demo.dto;

import java.util.List;

import com.example.demo.entity.Attention;
import com.example.demo.entity.InternshipStage;

public record StudentStatusDetailResponse(
        StudentProfileResponse student,
        RequestResponse application,
        List<PreferenceResponse> preferences,
        MatchResponse match,
        InternshipResponse internship,
        List<StatusHistoryResponse> timeline,
        InternshipStage stage,
        Attention attention
) {
}