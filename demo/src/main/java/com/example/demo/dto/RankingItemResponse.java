package com.example.demo.dto;

/** U4: บริษัทหนึ่งรายการในลำดับของนักศึกษา */
public record RankingItemResponse(Integer rank, Long companyId, String companyName) {
}
