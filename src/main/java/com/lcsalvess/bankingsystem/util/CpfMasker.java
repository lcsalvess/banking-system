package com.lcsalvess.bankingsystem.util;

public final class CpfMasker {

    private static final String MASKED_CPF = "***.***.***-**";

    private CpfMasker() {
    }

    public static String mask(String cpf) {
        if (cpf == null) {
            return null;
        }

        String digits = cpf.replaceAll("\\D", "");

        if (digits.length() != 11) {
            return MASKED_CPF;
        }

        return "***.***.***-" + digits.substring(9);
    }
}