package com.itx.attendance.service;

import com.itx.attendance.domain.Holiday;
import com.itx.attendance.dto.request.CreateHolidayRequest;
import com.itx.attendance.dto.response.HolidayDto;
import com.itx.attendance.exception.BusinessException;
import com.itx.attendance.repository.HolidayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HolidayService {

    private final HolidayRepository holidayRepository;

    public Page<HolidayDto> getAll(Integer year, Pageable pageable) {
        Page<Holiday> page = (year != null)
            ? holidayRepository.findByYear(year, pageable)
            : holidayRepository.findAll(pageable);
        return page.map(this::toDto);
    }

    @Transactional
    public HolidayDto create(CreateHolidayRequest request) {
        if (holidayRepository.existsByDate(request.date())) {
            throw new BusinessException(
                "Đã có ngày lễ cho ngày " + request.date(),
                HttpStatus.CONFLICT, "HOLIDAY_DATE_EXISTS");
        }

        Holiday holiday = Holiday.builder()
            .date(request.date())
            .name(request.name().strip())
            .type(request.type())
            .year(request.year())
            .build();

        try {
            Holiday saved = holidayRepository.save(holiday);
            return toDto(saved);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(
                "Đã có ngày lễ cho ngày này",
                HttpStatus.CONFLICT, "HOLIDAY_DATE_EXISTS");
        }
    }

    @Transactional
    public void delete(Long id) {
        Holiday holiday = holidayRepository.findById(id)
            .orElseThrow(() -> new BusinessException(
                "Ngày lễ không tồn tại",
                HttpStatus.NOT_FOUND, "HOLIDAY_NOT_FOUND"));
        holidayRepository.delete(holiday);
    }

    private HolidayDto toDto(Holiday h) {
        return HolidayDto.builder()
            .id(h.getId())
            .date(h.getDate())
            .name(h.getName())
            .type(h.getType())
            .year(h.getYear())
            .createdAt(h.getCreatedAt())
            .build();
    }
}
