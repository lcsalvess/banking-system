package com.lcsalvess.bankingsystem.unit.service.transaction.idempotency;

import com.lcsalvess.bankingsystem.dto.request.transaction.AccountOperationRequestDto;
import com.lcsalvess.bankingsystem.dto.request.transaction.TransferRequestDto;
import com.lcsalvess.bankingsystem.service.transaction.idempotency.TransactionRequestFingerprint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Transaction request fingerprint")
class TransactionRequestFingerprintTests {

    @Nested
    @DisplayName("Deposit fingerprints")
    class DepositTests {

        @Test
        @DisplayName("Should generate the same fingerprint for equivalent monetary values")
        void shouldGenerateSameFingerprintForEquivalentMonetaryValues() {
            AccountOperationRequestDto firstRequest =
                    operationRequest("00001", "9", "150.5");

            AccountOperationRequestDto secondRequest =
                    operationRequest("00001", "9", "150.50");

            assertEquals(
                    fingerprintForDeposit(firstRequest),
                    fingerprintForDeposit(secondRequest)
            );
        }

        @Test
        @DisplayName("Should generate different fingerprints for different amounts")
        void shouldGenerateDifferentFingerprintsForDifferentAmounts() {
            AccountOperationRequestDto firstRequest =
                    operationRequest("00001", "9", "150.00");

            AccountOperationRequestDto secondRequest =
                    operationRequest("00001", "9", "250.00");

            assertNotEquals(
                    fingerprintForDeposit(firstRequest),
                    fingerprintForDeposit(secondRequest)
            );
        }

        @Test
        @DisplayName("Should generate different fingerprints for different account numbers")
        void shouldGenerateDifferentFingerprintsForDifferentAccountNumbers() {
            AccountOperationRequestDto firstRequest =
                    operationRequest("00001", "9", "150.00");

            AccountOperationRequestDto secondRequest =
                    operationRequest("00002", "9", "150.00");

            assertNotEquals(
                    fingerprintForDeposit(firstRequest),
                    fingerprintForDeposit(secondRequest)
            );
        }

        @Test
        @DisplayName("Should generate different fingerprints for different account digits")
        void shouldGenerateDifferentFingerprintsForDifferentAccountDigits() {
            AccountOperationRequestDto firstRequest =
                    operationRequest("00001", "9", "150.00");

            AccountOperationRequestDto secondRequest =
                    operationRequest("00001", "8", "150.00");

            assertNotEquals(
                    fingerprintForDeposit(firstRequest),
                    fingerprintForDeposit(secondRequest)
            );
        }
    }

    @Nested
    @DisplayName("Withdrawal fingerprints")
    class WithdrawalTests {

        @Test
        @DisplayName("Should generate the same fingerprint for equivalent monetary values")
        void shouldGenerateSameFingerprintForEquivalentMonetaryValues() {
            AccountOperationRequestDto firstRequest =
                    operationRequest("00001", "9", "150.5");

            AccountOperationRequestDto secondRequest =
                    operationRequest("00001", "9", "150.50");

            assertEquals(
                    fingerprintForWithdrawal(firstRequest),
                    fingerprintForWithdrawal(secondRequest)
            );
        }

        @Test
        @DisplayName("Should generate different fingerprints for different amounts")
        void shouldGenerateDifferentFingerprintsForDifferentAmounts() {
            AccountOperationRequestDto firstRequest =
                    operationRequest("00001", "9", "150.00");

            AccountOperationRequestDto secondRequest =
                    operationRequest("00001", "9", "250.00");

            assertNotEquals(
                    fingerprintForWithdrawal(firstRequest),
                    fingerprintForWithdrawal(secondRequest)
            );
        }
    }

    @Nested
    @DisplayName("Transfer fingerprints")
    class TransferTests {

        @Test
        @DisplayName("Should generate the same fingerprint for equivalent monetary values")
        void shouldGenerateSameFingerprintForEquivalentMonetaryValues() {
            TransferRequestDto firstRequest =
                    transferRequest("00001", "9", "00002", "8", "150.5");

            TransferRequestDto secondRequest =
                    transferRequest("00001", "9", "00002", "8", "150.50");

            assertEquals(
                    fingerprintForTransfer(firstRequest),
                    fingerprintForTransfer(secondRequest)
            );
        }

        @Test
        @DisplayName("Should generate different fingerprints for different amounts")
        void shouldGenerateDifferentFingerprintsForDifferentAmounts() {
            TransferRequestDto firstRequest =
                    transferRequest("00001", "9", "00002", "8", "150.00");

            TransferRequestDto secondRequest =
                    transferRequest("00001", "9", "00002", "8", "250.00");

            assertNotEquals(
                    fingerprintForTransfer(firstRequest),
                    fingerprintForTransfer(secondRequest)
            );
        }

        @Test
        @DisplayName("Should generate different fingerprints for different source accounts")
        void shouldGenerateDifferentFingerprintsForDifferentSourceAccounts() {
            TransferRequestDto firstRequest =
                    transferRequest("00001", "9", "00002", "8", "150.00");

            TransferRequestDto secondRequest =
                    transferRequest("00003", "7", "00002", "8", "150.00");

            assertNotEquals(
                    fingerprintForTransfer(firstRequest),
                    fingerprintForTransfer(secondRequest)
            );
        }

        @Test
        @DisplayName("Should generate different fingerprints for different source account digits")
        void shouldGenerateDifferentFingerprintsForDifferentSourceAccountDigits() {
            TransferRequestDto firstRequest =
                    transferRequest("00001", "9", "00002", "8", "150.00");

            TransferRequestDto secondRequest =
                    transferRequest("00001", "8", "00002", "8", "150.00");

            assertNotEquals(
                    fingerprintForTransfer(firstRequest),
                    fingerprintForTransfer(secondRequest)
            );
        }

        @Test
        @DisplayName("Should generate different fingerprints for different destination accounts")
        void shouldGenerateDifferentFingerprintsForDifferentDestinationAccounts() {
            TransferRequestDto firstRequest =
                    transferRequest("00001", "9", "00002", "8", "150.00");

            TransferRequestDto secondRequest =
                    transferRequest("00001", "9", "00003", "7", "150.00");

            assertNotEquals(
                    fingerprintForTransfer(firstRequest),
                    fingerprintForTransfer(secondRequest)
            );
        }

        @Test
        @DisplayName("Should generate different fingerprints for different destination account digits")
        void shouldGenerateDifferentFingerprintsForDifferentDestinationAccountDigits() {
            TransferRequestDto firstRequest =
                    transferRequest("00001", "9", "00002", "8", "150.00");

            TransferRequestDto secondRequest =
                    transferRequest("00001", "9", "00002", "7", "150.00");

            assertNotEquals(
                    fingerprintForTransfer(firstRequest),
                    fingerprintForTransfer(secondRequest)
            );
        }
    }

    @Nested
    @DisplayName("Fingerprint operation identity")
    class OperationIdentityTests {

        @Test
        @DisplayName("Should generate different fingerprints for deposit and withdrawal")
        void shouldGenerateDifferentFingerprintsForDifferentOperationTypes() {
            AccountOperationRequestDto request =
                    operationRequest("00001", "9", "150.00");

            assertNotEquals(
                    fingerprintForDeposit(request),
                    fingerprintForWithdrawal(request)
            );
        }
    }

    @Nested
    @DisplayName("Fingerprint format")
    class FingerprintFormatTests {

        @Test
        @DisplayName("Should generate a 64-character lowercase SHA-256 hexadecimal fingerprint")
        void shouldGenerateSha256FingerprintInExpectedFormat() {
            AccountOperationRequestDto request =
                    operationRequest("00001", "9", "150.00");

            String fingerprint = fingerprintForDeposit(request);

            assertTrue(fingerprint.matches("[0-9a-f]{64}"));
        }

        @Test
        @DisplayName("Should generate a deterministic fingerprint for the same request")
        void shouldGenerateDeterministicFingerprintForSameRequest() {
            AccountOperationRequestDto request =
                    operationRequest("00001", "9", "150.00");

            assertEquals(
                    fingerprintForDeposit(request),
                    fingerprintForDeposit(request)
            );
        }
    }

    private static AccountOperationRequestDto operationRequest(
            String accountNumber,
            String digit,
            String amount
    ) {
        return new AccountOperationRequestDto(
                accountNumber,
                digit,
                new BigDecimal(amount)
        );
    }

    private static TransferRequestDto transferRequest(
            String fromAccountNumber,
            String fromAccountDigit,
            String toAccountNumber,
            String toAccountDigit,
            String amount
    ) {
        return new TransferRequestDto(
                fromAccountNumber,
                fromAccountDigit,
                toAccountNumber,
                toAccountDigit,
                new BigDecimal(amount)
        );
    }

    private static String fingerprintForDeposit(
            AccountOperationRequestDto request
    ) {
        return TransactionRequestFingerprint.forDeposit(request);
    }

    private static String fingerprintForWithdrawal(
            AccountOperationRequestDto request
    ) {
        return TransactionRequestFingerprint.forWithdrawal(request);
    }

    private static String fingerprintForTransfer(
            TransferRequestDto request
    ) {
        return TransactionRequestFingerprint.forTransfer(request);
    }
}
