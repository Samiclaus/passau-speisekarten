package de.passau.speisekarten.mensa;

import java.math.BigDecimal;

/** One dish row from the rendered STWNO menu. Missing prices remain null. */
public record MensaHtmlDish(
    String category,
    String name,
    java.util.Set<MensaMarker> markers,
    BigDecimal studentPrice,
    BigDecimal employeePrice,
    BigDecimal guestPrice)
{
}
