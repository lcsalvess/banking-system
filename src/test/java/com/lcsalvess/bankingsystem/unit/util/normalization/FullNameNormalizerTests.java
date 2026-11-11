package com.lcsalvess.bankingsystem.unit.util.normalization;

import com.lcsalvess.bankingsystem.util.normalization.FullNameNormalizer;
import com.lcsalvess.bankingsystem.validation.ValidationMessages;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FullNameNormalizerTests {

    // Expected message after formatting the %d placeholder with the limit.
    private static final String TOO_LONG =
            ValidationMessages.INVALID_FULL_NAME_LENGTH
                    .formatted(FullNameNormalizer.MAX_LENGTH);

    // U+20000 (CJK Extension B, category Lo): a letter outside the BMP,
    // encoded as a surrogate pair (2 UTF-16 units, 1 code point).
    private static final String SUPPLEMENTARY_LETTER = "\uD840\uDC00";

    private static void assertRejected(String input, String expectedMessage) {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> FullNameNormalizer.normalize(input)
        );
        assertEquals(expectedMessage, ex.getMessage());
    }

    @Test
    @DisplayName("Utility class has a private constructor")
    void hasPrivateConstructor() throws Exception {
        Constructor<FullNameNormalizer> constructor =
                FullNameNormalizer.class.getDeclaredConstructor();

        assertTrue(Modifier.isPrivate(constructor.getModifiers()));

        constructor.setAccessible(true);
        constructor.newInstance();
    }

    @Nested
    @DisplayName("Null and blank input")
    class NullAndBlank {

        @Test
        void rejectsNull() {
            assertRejected(null, ValidationMessages.REQUIRED_FULL_NAME);
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "",
                "   ",
                "\u00A0",          // no-break space
                "\u2003\u2009",    // em space + thin space
                "\u3000",          // ideographic space
                "\u2028",          // line separator (not a control char)
                "\u2029"           // paragraph separator (not a control char)
        })
        void rejectsBlankValues(String input) {
            assertRejected(input, ValidationMessages.INVALID_FULL_NAME_BLANK);
        }
    }

    @Nested
    @DisplayName("Whitespace and Unicode normalization")
    class Normalization {

        @Test
        void keepsAlreadyNormalizedName() {
            assertEquals(
                    "José da Silva",
                    FullNameNormalizer.normalize("José da Silva")
            );
        }

        @Test
        void trimsAndCollapsesRegularSpaces() {
            assertEquals(
                    "Maria da Silva",
                    FullNameNormalizer.normalize("  Maria     da   Silva  ")
            );
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "Maria\u00A0Silva",   // no-break space
                "Maria\u2003Silva",   // em space
                "Maria\u3000Silva",   // ideographic space
                "Maria\u2028Silva",   // line separator
                "Maria\u2029Silva"    // paragraph separator
        })
        void convertsUnicodeWhitespaceToSingleSpace(String input) {
            assertEquals("Maria Silva", FullNameNormalizer.normalize(input));
        }

        @Test
        void convertsDecomposedFormToNfc() {
            // "e" + combining acute accent must equal the precomposed "é".
            String result = FullNameNormalizer.normalize("Jose\u0301");

            assertEquals("Jos\u00E9", result);
            assertEquals(4, result.length());
        }

        @Test
        void preservesCaseAndAccents() {
            assertEquals(
                    "ÂNGELA conceição",
                    FullNameNormalizer.normalize("ÂNGELA conceição")
            );
        }

        @Test
        void isIdempotent() {
            String once = FullNameNormalizer.normalize(
                    "  Jose\u0301   da\u00A0Silva ");

            assertEquals(once, FullNameNormalizer.normalize(once));
        }
    }

    @Nested
    @DisplayName("Accepted names")
    class Accepted {

        @ParameterizedTest
        @ValueSource(strings = {
                "Maria-Clara D'Ávila",
                "Ana Maria Jr.",
                "李小龍",
                "Ольга Иванова",
                "محمد علي",
                "می\u200Cخواهم",             // ZWNJ between letters (Persian)
                "ক\u200Dষ",                  // ZWJ between letters
                "\uD840\uDC00 Silva"         // supplementary-plane letter (Lo)
        })
        void acceptsValidNames(String input) {
            assertEquals(input, FullNameNormalizer.normalize(input));
        }
    }

    @Nested
    @DisplayName("Invalid code points")
    class InvalidCodePoints {

        @ParameterizedTest
        @ValueSource(strings = {
                // CONTROL
                "Maria\u0000Silva",    // NUL
                "Maria\u0007Silva",    // BEL
                "Maria\tSilva",        // tab
                "Maria\nSilva",        // line feed
                "Maria\rSilva",        // carriage return
                "Maria\u0085Silva",    // NEL
                "Maria \n",            // trailing newline is not trimmed
                // FORMAT (other than ZWJ/ZWNJ)
                "\u200B",              // zero-width space
                "Maria\u200BSilva",
                "\uFEFFMaria",         // BOM
                "Maria\u202ESilva",    // right-to-left override
                "Maria\u00ADSilva",    // soft hyphen
                // SURROGATE
                "\uD800",              // isolated high surrogate
                "Maria \uDC00"         // isolated low surrogate
        })
        void rejectsInvalidCodePoints(String input) {
            assertRejected(
                    input,
                    ValidationMessages.INVALID_FULL_NAME_CHARACTERS
            );
        }
    }

    @Nested
    @DisplayName("Symbols and emoji presentation")
    class Symbols {

        @ParameterizedTest
        @ValueSource(strings = {
                // OTHER_SYMBOL
                "Maria \uD83D\uDE00",  // emoji (valid surrogate pair)
                "Maria\u00A9",         // copyright sign
                // MATH_SYMBOL
                "Maria+Silva",
                "<Maria>",
                // CURRENCY_SYMBOL
                "Maria\u20AC",         // euro sign
                "Maria$",
                // MODIFIER_SYMBOL
                "Maria^Silva",
                "Maria`Silva",
                // Emoji presentation helpers (not symbols by category)
                "Maria\uFE0E",         // text variation selector
                "Maria\uFE0F",         // emoji variation selector
                "Maria\u20E3"          // combining enclosing keycap
        })
        void rejectsSymbolsAndEmojiPresentation(String input) {
            assertRejected(
                    input,
                    ValidationMessages.INVALID_FULL_NAME_CHARACTERS
            );
        }

        @Test
        void rejectsKeycapSequenceBeforeNumericCheck() {
            // "1" + emoji selector + keycap: the invalid-character rule fires first.
            assertRejected(
                    "1\uFE0F\u20E3",
                    ValidationMessages.INVALID_FULL_NAME_CHARACTERS
            );
        }
    }

    @Nested
    @DisplayName("Numeric characters")
    class Numeric {

        @ParameterizedTest
        @ValueSource(strings = {
                "João 123",
                "123",
                "Maria\u0663",         // Arabic-Indic digit (DECIMAL_DIGIT_NUMBER)
                "Maria \u216B",        // Roman numeral twelve (LETTER_NUMBER)
                "Maria\u00B2"          // superscript two (OTHER_NUMBER)
        })
        void rejectsNumericCharacters(String input) {
            assertRejected(input, ValidationMessages.INVALID_FULL_NAME_NUMERIC);
        }
    }

    @Nested
    @DisplayName("Letter requirement")
    class LetterRequirement {

        @ParameterizedTest
        @ValueSource(strings = {
                "\u200D",              // lone ZWJ
                "\u200C",              // lone ZWNJ
                "\u200C\u200D",        // ZWNJ + ZWJ
                "-",
                "' . -",
                "..."
        })
        void rejectsNamesWithoutLetters(String input) {
            assertRejected(input, ValidationMessages.INVALID_FULL_NAME_NO_LETTERS);
        }
    }

    @Nested
    @DisplayName("Length limit")
    class Length {

        @Test
        void acceptsExactlyMaxLength() {
            String input = "a".repeat(FullNameNormalizer.MAX_LENGTH);

            assertEquals(input, FullNameNormalizer.normalize(input));
        }

        @Test
        void rejectsOneOverMaxLength() {
            assertRejected(
                    "a".repeat(FullNameNormalizer.MAX_LENGTH + 1),
                    TOO_LONG
            );
        }

        @Test
        void countsCodePointsNotUtf16Units() {
            // (MAX - 1) chars + 1 supplementary letter = MAX code points,
            // but MAX + 1 UTF-16 units.
            String atLimit =
                    "a".repeat(FullNameNormalizer.MAX_LENGTH - 1)
                            + SUPPLEMENTARY_LETTER;
            // MAX chars + 1 supplementary letter = MAX + 1 code points.
            String overLimit =
                    "a".repeat(FullNameNormalizer.MAX_LENGTH)
                            + SUPPLEMENTARY_LETTER;

            assertEquals(atLimit, FullNameNormalizer.normalize(atLimit));
            assertRejected(overLimit, TOO_LONG);
        }

        @Test
        void measuresLengthAfterNfc() {
            // Decomposed form has 2 * MAX UTF-16 units but only MAX after NFC.
            String decomposedAtLimit =
                    "e\u0301".repeat(FullNameNormalizer.MAX_LENGTH);
            String decomposedOverLimit =
                    "e\u0301".repeat(FullNameNormalizer.MAX_LENGTH + 1);

            assertEquals(
                    FullNameNormalizer.MAX_LENGTH,
                    FullNameNormalizer.normalize(decomposedAtLimit).length()
            );
            assertRejected(decomposedOverLimit, TOO_LONG);
        }

        @Test
        void measuresLengthAfterWhitespaceCollapse() {
            // Raw input exceeds the limit, but collapsed whitespace brings it under.
            String input = "a" + " ".repeat(500) + "b";

            assertEquals("a b", FullNameNormalizer.normalize(input));
        }
    }

    @Nested
    @DisplayName("Validation order")
    class Order {

        @Test
        void invalidCharactersWinOverBlank() {
            // A lone tab is a control character, so it is not treated as blank.
            assertRejected(
                    "\t",
                    ValidationMessages.INVALID_FULL_NAME_CHARACTERS
            );
        }

        @Test
        void invalidCharactersWinOverNumeric() {
            assertRejected(
                    "Maria\u0000 123",
                    ValidationMessages.INVALID_FULL_NAME_CHARACTERS
            );
        }

        @Test
        void invalidCharactersWinOverMissingLetter() {
            // "+" is a symbol, so it is rejected before the letter requirement.
            assertRejected(
                    "+",
                    ValidationMessages.INVALID_FULL_NAME_CHARACTERS
            );
        }

        @Test
        void numericWinsOverMissingLetter() {
            assertRejected(
                    "123\u200D",
                    ValidationMessages.INVALID_FULL_NAME_NUMERIC
            );
        }

        @Test
        void missingLetterWinsOverLength() {
            assertRejected(
                    "-".repeat(FullNameNormalizer.MAX_LENGTH + 1),
                    ValidationMessages.INVALID_FULL_NAME_NO_LETTERS
            );
        }
    }
}
