package com.example.demo.dto;

/**
 * U6: อันดับที่นักศึกษาเลือก
 * resultStatus: PENDING = ยังไม่ประกาศผล · MATCHED = ได้อันดับนี้ · NOT_SELECTED = ไม่ได้อันดับนี้
 */
public record PreferenceResponse(int rank, Long positionId, String positionName, String companyName,
        String resultStatus) {

    public static final String PENDING = "PENDING";
    public static final String MATCHED = "MATCHED";
    public static final String NOT_SELECTED = "NOT_SELECTED";
}
