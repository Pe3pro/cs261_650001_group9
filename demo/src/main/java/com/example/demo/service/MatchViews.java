package com.example.demo.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.example.demo.dao.MatchingRoundRepository;
import com.example.demo.dao.StudentRepository;
import com.example.demo.dao.UserRepository;
import com.example.demo.dto.MatchResponse;
import com.example.demo.dto.PreferenceResponse;
import com.example.demo.entity.Application;
import com.example.demo.entity.Company;
import com.example.demo.entity.CompanyPosition;
import com.example.demo.entity.CompanyPreference;
import com.example.demo.entity.Match;
import com.example.demo.entity.MatchStatus;
import com.example.demo.entity.MatchingRound;
import com.example.demo.entity.Student;
import com.example.demo.entity.User;
import com.example.demo.repository.CompanyPositionRepository;
import com.example.demo.repository.CompanyRepository;

/** U6: แปลงข้อมูลการจับคู่เป็น DTO (ชื่อนักศึกษา ตำแหน่ง บริษัท รอบ) ใช้ร่วมกันระหว่าง PreferenceService และ MatchingService */
@Component
public class MatchViews {

    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final CompanyPositionRepository positionRepository;
    private final CompanyRepository companyRepository;
    private final MatchingRoundRepository roundRepository;

    public MatchViews(StudentRepository studentRepository, UserRepository userRepository,
            CompanyPositionRepository positionRepository, CompanyRepository companyRepository,
            MatchingRoundRepository roundRepository) {
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
        this.positionRepository = positionRepository;
        this.companyRepository = companyRepository;
        this.roundRepository = roundRepository;
    }

    /** รหัสนักศึกษา และชื่อ-นามสกุลของเจ้าของใบสมัคร */
    public String[] studentOf(Application app) {
        Optional<Student> student = studentRepository.findById(app.getStudentId());
        String code = student.map(Student::getStudentId).orElse(null);
        String name = student.map(Student::getUserId).flatMap(userRepository::findById)
                .map(User::getFullName).orElse(code);
        return new String[] { code, name };
    }

    /** "ตำแหน่ง X บริษัท Y" ใช้ในข้อความแจ้งเตือน */
    public String placeOf(Long positionId) {
        Optional<CompanyPosition> p = positionRepository.findById(positionId);
        String position = p.map(CompanyPosition::getPositionName).orElse("-");
        String company = p.map(CompanyPosition::getCompanyId).flatMap(companyRepository::findById)
                .map(Company::getCompanyName).orElse("-");
        return "ตำแหน่ง " + position + " บริษัท " + company;
    }

    public MatchResponse toResponse(Match m, Application app) {
        String[] student = app == null ? new String[] { null, null } : studentOf(app);
        Optional<CompanyPosition> p = positionRepository.findById(m.getPositionId());
        String company = p.map(CompanyPosition::getCompanyId).flatMap(companyRepository::findById)
                .map(Company::getCompanyName).orElse(null);
        Optional<MatchingRound> round = roundRepository.findById(m.getRoundId());
        return new MatchResponse(m.getId(), m.getApplicationId(), student[0], student[1], m.getPositionId(),
                p.map(CompanyPosition::getPositionName).orElse(null), company, m.getRankNo(), m.getStatus(),
                m.getRoundId(), round.map(MatchingRound::getRoundNo).orElse(null), m.getMatchedAt(),
                m.getRespondedAt(), round.map(MatchingRound::getConfirmDeadline).orElse(null), m.getDeclineReason());
    }

    /**
     * อันดับที่เลือก พร้อมผลรายอันดับ
     * announced = ผลล่าสุดที่ประกาศแล้ว (null = ยังไม่ประกาศ → ทุกอันดับ PENDING)
     */
    public List<PreferenceResponse> preferences(List<CompanyPreference> prefs, Match announced) {
        Map<Long, CompanyPosition> positions = new HashMap<>();
        positionRepository.findAllById(prefs.stream().map(CompanyPreference::getPositionId).toList())
                .forEach(p -> positions.put(p.getId(), p));
        Map<Long, String> companies = new HashMap<>();
        return prefs.stream().map(pref -> {
            CompanyPosition p = positions.get(pref.getPositionId());
            String company = p == null ? null : companies.computeIfAbsent(p.getCompanyId(),
                    id -> companyRepository.findById(id).map(Company::getCompanyName).orElse(null));
            String result = PreferenceResponse.PENDING;
            if (announced != null && announced.getStatus() != MatchStatus.CANCELLED) {
                result = pref.getPositionId().equals(announced.getPositionId())
                        ? PreferenceResponse.MATCHED : PreferenceResponse.NOT_SELECTED;
            }
            return new PreferenceResponse(pref.getRankNo(), pref.getPositionId(),
                    p == null ? null : p.getPositionName(), company, result);
        }).toList();
    }
}
