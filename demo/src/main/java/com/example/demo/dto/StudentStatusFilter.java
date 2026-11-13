package com.example.demo.dto;

import com.example.demo.entity.InternshipStage;

public record StudentStatusFilter(
        InternshipStage stage,
        Long companyId,
        String q,
        Long advisorId,
        int page,
        int size
) {

    public StudentStatusFilter {
        q = q == null ? null : q.trim();
    }
}