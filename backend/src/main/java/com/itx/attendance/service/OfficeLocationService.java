package com.itx.attendance.service;

import com.itx.attendance.domain.OfficeLocation;
import com.itx.attendance.dto.request.CreateOfficeLocationRequest;
import com.itx.attendance.dto.response.OfficeLocationDto;
import com.itx.attendance.exception.BusinessException;
import com.itx.attendance.repository.OfficeLocationRepository;
import com.itx.attendance.util.HaversineUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OfficeLocationService {

    private final OfficeLocationRepository officeLocationRepository;

    @Transactional
    public OfficeLocationDto create(CreateOfficeLocationRequest req) {
        validateRadiusBounds(req.radiusMeters());
        OfficeLocation location = OfficeLocation.builder()
            .name(req.name().strip())
            .latitude(req.latitude())
            .longitude(req.longitude())
            .radiusMeters(req.radiusMeters())
            .active(req.isActive() == null || req.isActive())
            .build();
        return toDto(officeLocationRepository.save(location));
    }

    @Transactional(readOnly = true)
    public List<OfficeLocationDto> findAll() {
        return officeLocationRepository.findAllByOrderByNameAsc().stream()
            .map(this::toDto)
            .toList();
    }

    @Transactional
    public OfficeLocationDto update(Long id, CreateOfficeLocationRequest req) {
        validateRadiusBounds(req.radiusMeters());
        OfficeLocation location = officeLocationRepository.findById(id)
            .orElseThrow(() -> new BusinessException(
                "Office location not found", HttpStatus.NOT_FOUND, "OFFICE_LOCATION_NOT_FOUND"));
        location.setName(req.name().strip());
        location.setLatitude(req.latitude());
        location.setLongitude(req.longitude());
        location.setRadiusMeters(req.radiusMeters());
        if (req.isActive() != null) {
            location.setActive(req.isActive());
        }
        return toDto(officeLocationRepository.save(location));
    }

    @Transactional
    public void delete(Long id) {
        officeLocationRepository.findById(id)
            .orElseThrow(() -> new BusinessException(
                "Office location not found", HttpStatus.NOT_FOUND, "OFFICE_LOCATION_NOT_FOUND"));
        officeLocationRepository.deleteById(id);
    }

    public void validateRadius(double lat, double lng) {
        List<OfficeLocation> activeLocations = officeLocationRepository.findByActiveTrue();
        if (activeLocations.isEmpty()) return;
        double minDistKm = Double.MAX_VALUE;
        for (OfficeLocation loc : activeLocations) {
            double distKm = HaversineUtil.distanceKm(
                lat, lng,
                loc.getLatitude().doubleValue(),
                loc.getLongitude().doubleValue());
            if (distKm <= loc.getRadiusMeters() / 1000.0) return;
            if (distKm < minDistKm) minDistKm = distKm;
        }
        long distM = Math.round(minDistKm * 1000);
        throw new BusinessException(
            "Vị trí của bạn không nằm trong phạm vi văn phòng (" + distM + "m)",
            HttpStatus.BAD_REQUEST, "OUT_OF_OFFICE_RADIUS");
    }

    private void validateRadiusBounds(Integer radiusMeters) {
        if (radiusMeters != null && (radiusMeters < 50 || radiusMeters > 5000)) {
            throw new BusinessException(
                "Bán kính không hợp lệ (phải từ 50 đến 5000 mét)",
                HttpStatus.BAD_REQUEST, "INVALID_RADIUS");
        }
    }

    private OfficeLocationDto toDto(OfficeLocation loc) {
        return OfficeLocationDto.builder()
            .id(loc.getId())
            .name(loc.getName())
            .latitude(loc.getLatitude())
            .longitude(loc.getLongitude())
            .radiusMeters(loc.getRadiusMeters())
            .active(loc.isActive())
            .createdAt(loc.getCreatedAt())
            .build();
    }
}
