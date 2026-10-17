package com.lcsalvess.bankingsystem.util;

/**
 * Identificador do ator das operações geradas pelo próprio sistema, como o
 * rendimento diário. É o valor registrado nos logs no lugar do nome de um
 * usuário, e por isso o nome de usuário equivalente é reservado.
 */
public final class SystemActor {

    public static final String NAME = "SYSTEM";

    private SystemActor() {
    }
}