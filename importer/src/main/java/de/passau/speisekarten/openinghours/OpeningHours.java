package de.passau.speisekarten.openinghours;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/** Reusable opening/service hours. Dated exceptions override weekly regular hours. */
public record OpeningHours(
    Map<DayOfWeek, List<TimeRange>> regularOpeningHours,
    Map<DayOfWeek, List<TimeRange>> regularServiceHours,
    List<DatedException> exceptions,
    String sourceUrl,
    String seasonalRuleNote
) {
    public OpeningHours {
        regularOpeningHours = regularOpeningHours == null ? Map.of() : Map.copyOf(regularOpeningHours);
        regularServiceHours = regularServiceHours == null ? Map.of() : Map.copyOf(regularServiceHours);
        exceptions = exceptions == null ? List.of() : List.copyOf(exceptions);
    }

    public record TimeRange(LocalTime from, LocalTime to) {
        public TimeRange {
            if (from == null || to == null || !to.isAfter(from))
                throw new IllegalArgumentException("Opening-hours range must have a start before its end");
        }
    }

    /** Empty list means explicitly closed; null means unspecified. */
    public record DatedException(LocalDate fromDate, LocalDate toDate, List<TimeRange> openingHours,
                                 List<TimeRange> serviceHours, String note, String sourceUrl) {
        public DatedException {
            if (fromDate == null || toDate == null || toDate.isBefore(fromDate))
                throw new IllegalArgumentException("Invalid exception date range");
            openingHours = openingHours == null ? null : List.copyOf(openingHours);
            serviceHours = serviceHours == null ? null : List.copyOf(serviceHours);
        }
    }
}