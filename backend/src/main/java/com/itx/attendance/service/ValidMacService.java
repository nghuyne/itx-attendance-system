package com.itx.attendance.service;

import com.itx.attendance.domain.ValidMac;
import com.itx.attendance.dto.request.CreateValidMacRequest;
import com.itx.attendance.dto.response.ValidMacDto;
import com.itx.attendance.exception.BusinessException;
import com.itx.attendance.repository.ValidMacRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ValidMacService {

    private final ValidMacRepository validMacRepository;

    @Transactional(readOnly = true)
    public List<ValidMacDto> getAll() {
        return validMacRepository.findByActiveTrue().stream()
            .map(this::toDto)
            .toList();
    }

    @Transactional
    public ValidMacDto add(CreateValidMacRequest request, String adminUsername) {
        String normalizedBssid = request.bssid().strip().toUpperCase();

        if (validMacRepository.existsByBssidAndActiveTrue(normalizedBssid)) {
            throw new BusinessException(
                "BSSID này đã tồn tại trong danh sách",
                HttpStatus.CONFLICT, "DUPLICATE_MAC");
        }

        ValidMac mac = ValidMac.builder()
            .bssid(normalizedBssid)
            .description(request.description())
            .createdBy(adminUsername)
            .build();

        try {
            return toDto(validMacRepository.save(mac));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(
                "BSSID này đã tồn tại (duplicate)",
                HttpStatus.CONFLICT, "DUPLICATE_MAC");
        }
    }

    @Transactional
    public void delete(Long id) {
        ValidMac mac = validMacRepository.findById(id)
            .orElseThrow(() -> new BusinessException(
                "MAC không tồn tại", HttpStatus.NOT_FOUND, "MAC_NOT_FOUND"));
        mac.setActive(false);
        validMacRepository.save(mac);
    }

    private ValidMacDto toDto(ValidMac mac) {
        return ValidMacDto.builder()
            .id(mac.getId())
            .bssid(mac.getBssid())
            .description(mac.getDescription())
            .createdBy(mac.getCreatedBy())
            .createdAt(mac.getCreatedAt())
            .build();
    }
}
