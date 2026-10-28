package com.lcsalvess.bankingsystem.util.time;

import java.time.ZoneId;

/**
 * Fonte única do fuso horário da aplicação. Usada pelo {@code Clock} e pelas
 * conversões de data local para instante (filtros de período e início do dia).
 */
public final class AppTimeZone {

    public static final String ID = "America/Sao_Paulo";

    public static final ZoneId ZONE = ZoneId.of(ID);

    private AppTimeZone() {
    }
}
