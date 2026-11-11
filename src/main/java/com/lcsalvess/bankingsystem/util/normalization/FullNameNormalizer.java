package com.lcsalvess.bankingsystem.util.normalization;

import com.lcsalvess.bankingsystem.validation.ValidationMessages;

import java.text.Normalizer;
import java.util.regex.Pattern;

public final class FullNameNormalizer {

    public static final int MAX_LENGTH = 150;

    private static final int ZERO_WIDTH_NON_JOINER = 0x200C;
    private static final int ZERO_WIDTH_JOINER = 0x200D;

    private static final int VARIATION_SELECTOR_TEXT = 0xFE0E;
    private static final int VARIATION_SELECTOR_EMOJI = 0xFE0F;
    private static final int COMBINING_ENCLOSING_KEYCAP = 0x20E3;

    private static final Pattern WHITESPACE =
            Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

    private FullNameNormalizer() {
    }

    public static String normalize(String fullName) {
        if (fullName == null) {
            throw new IllegalArgumentException(
                    ValidationMessages.REQUIRED_FULL_NAME
            );
        }

        // Normalize canonically equivalent Unicode representations.
        // Accented characters are preserved.
        String normalized = Normalizer.normalize(
                fullName,
                Normalizer.Form.NFC
        );

        // Reject control characters, unsupported format characters,
        // Unicode symbols, emoji presentation selectors,
        // and isolated UTF-16 surrogate code points.
        if (containsInvalidCodePoints(normalized)) {
            throw new IllegalArgumentException(
                    ValidationMessages.INVALID_FULL_NAME_CHARACTERS
            );
        }

        // Collapse consecutive Unicode whitespace and remove
        // leading and trailing whitespace.
        String result = WHITESPACE.matcher(normalized)
                .replaceAll(" ")
                .strip();

        if (result.isEmpty()) {
            throw new IllegalArgumentException(
                    ValidationMessages.INVALID_FULL_NAME_BLANK
            );
        }

        // Reject numeric characters from all Unicode number categories.
        if (containsNumericCharacters(result)) {
            throw new IllegalArgumentException(
                    ValidationMessages.INVALID_FULL_NAME_NUMERIC
            );
        }

        // Require at least one Unicode letter.
        // Accented and non-Latin letters are supported.
        if (result.codePoints().noneMatch(Character::isLetter)) {
            throw new IllegalArgumentException(
                    ValidationMessages.INVALID_FULL_NAME_NO_LETTERS
            );
        }

        // Count Unicode code points rather than UTF-16 code units.
        if (result.codePointCount(0, result.length()) > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    ValidationMessages.INVALID_FULL_NAME_LENGTH
                            .formatted(MAX_LENGTH)
            );
        }

        return result;
    }

    private static boolean containsInvalidCodePoints(String value) {
        return value.codePoints().anyMatch(codePoint -> {
            int type = Character.getType(codePoint);

            if (type == Character.CONTROL
                    || type == Character.SURROGATE
                    || isSymbol(type)) {
                return true;
            }

            if (codePoint == VARIATION_SELECTOR_TEXT
                    || codePoint == VARIATION_SELECTOR_EMOJI
                    || codePoint == COMBINING_ENCLOSING_KEYCAP) {
                return true;
            }

            return type == Character.FORMAT
                    && codePoint != ZERO_WIDTH_NON_JOINER
                    && codePoint != ZERO_WIDTH_JOINER;
        });
    }

    private static boolean isSymbol(int type) {
        return type == Character.MATH_SYMBOL
                || type == Character.CURRENCY_SYMBOL
                || type == Character.MODIFIER_SYMBOL
                || type == Character.OTHER_SYMBOL;
    }

    private static boolean containsNumericCharacters(String value) {
        return value.codePoints().anyMatch(codePoint -> {
            int type = Character.getType(codePoint);

            return type == Character.DECIMAL_DIGIT_NUMBER
                    || type == Character.LETTER_NUMBER
                    || type == Character.OTHER_NUMBER;
        });
    }
}
