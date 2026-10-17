package com.lcsalvess.bankingsystem.util;

import java.time.ZoneId;

/**
 * Fonte única do fuso horário da aplicação. Usada pelo {@code Clock} e pelos
 * callbacks de entidade, que não conseguem receber o Clock por injeção.
 */
public final class AppTimeZone {

    public static final String ID = "America/Sao_Paulo";

    public static final ZoneId ZONE = ZoneId.of(ID);

    private AppTimeZone() {
    }
}
