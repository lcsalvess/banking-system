package com.lcsalvess.bankingsystem.util.time;

import java.time.ZoneId;

/**
 * Single source of truth for the application's time zone.
 *
 * <p>Used by the {@code Clock} and for converting local dates into instants
 * (period filters and start-of-day calculations).
 */
public final class AppTimeZone {

    public static final String ID = "America/Sao_Paulo";

    public static final ZoneId ZONE = ZoneId.of(ID);

    private AppTimeZone() {
    }
}
