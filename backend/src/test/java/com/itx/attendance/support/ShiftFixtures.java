package com.itx.attendance.support;

import com.itx.attendance.domain.Shift;

import java.time.LocalTime;

/**
 * Shared builders for the "Ca Sáng" (08:00-17:00) and "Ca Đêm" (22:00-23:30) shift
 * fixtures that were previously copy-pasted verbatim across ~10 controller/service/job
 * test classes. Returns a {@link Shift.ShiftBuilder} from the base factory so callers
 * that need extra fields (e.g. a test-specific name) can still customize before build().
 */
public final class ShiftFixtures {

    private ShiftFixtures() {
    }

    public static Shift.ShiftBuilder daySchedule() {
        return Shift.builder()
            .name("Ca Sáng")
            .shiftStartTime(LocalTime.of(8, 0))
            .shiftEndTime(LocalTime.of(17, 0));
    }

    public static Shift daySchedule(String id) {
        return daySchedule().id(id).build();
    }

    public static Shift daySchedule(int otBuffer) {
        return daySchedule().otBuffer(otBuffer).build();
    }

    public static Shift daySchedule(String id, int otBuffer) {
        return daySchedule().id(id).otBuffer(otBuffer).build();
    }

    public static Shift nightSchedule(String id) {
        return Shift.builder()
            .id(id)
            .name("Ca Đêm")
            .shiftStartTime(LocalTime.of(22, 0))
            .shiftEndTime(LocalTime.of(23, 30))
            .otBuffer(60)
            .build();
    }
}
