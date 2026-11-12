package com.example.demo.dto;

import java.util.List;

/** U6: PUT /api/applications/{id}/preferences · positionIds[0] = อันดับ 1 (1–5 ตำแหน่ง ไม่ซ้ำ) */
public record PreferenceRequest(List<Long> positionIds) {
}
