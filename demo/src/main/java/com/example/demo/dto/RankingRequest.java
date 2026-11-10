package com.example.demo.dto;

import java.util.List;

/**
 * U4: PUT /api/students/me/company-ranking
 * companyIds เรียงตามอันดับ: ตัวแรก = อันดับ 1 ... ตัวที่ 5 = อันดับ 5
 * ตัวอย่าง {"companyIds": [3, 1, 5, 2, 4]}
 */
public record RankingRequest(List<Long> companyIds) {
}
