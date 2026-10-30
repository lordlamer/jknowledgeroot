package org.knowledgeroot.app.security.mfa;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.OptionalLong;

/**
 * Enrolment and verification of a user's second factor. The TOTP secret is stored encrypted,
 * recovery codes as keyed hashes; each time step and each recovery code is accepted once.
 */
@Service
@Slf4j
public class SecondFactorService {
    public static final int MAX_INPUT_LENGTH = 32;
    private final SecondFactorRepository factors;
    private final SecretCipher cipher;
    private final Clock clock;

    public SecondFactorService(SecondFactorRepository factors, SecretCipher cipher, ObjectProvider<Clock> clock) {
        this.factors = factors;
        this.cipher = cipher;
        this.clock = clock.getIfAvailable(Clock::systemUTC);
    }

    public record Status(boolean enrolled, LocalDateTime enrolledAt, int recoveryCodesLeft) {}

    /** A pending enrolment: the caller keeps {@code secret} until the first code confirms it. */
    public record Enrolment(String secret, String otpauthUri, String qrSvg) {}

    public enum Result { TOTP_OK, RECOVERY_CODE_OK, INVALID }

    public Status status(int userId) {
        return factors.find(userId)
                .map(factor -> new Status(true, factor.enrolledAt(), factors.unusedCodes(userId)))
                .orElse(new Status(false, null, 0));
    }

    public boolean enrolled(int userId) {
        return factors.find(userId).isPresent();
    }

    public Enrolment begin(String login) {
        TotpSecret secret = TotpSecret.generate();
        String uri = Totp.otpauthUri(login, secret);
        return new Enrolment(secret.base32(), uri, QrCode.svg(uri));
    }

    /**
     * Confirms a pending enrolment with the first code and returns the plain recovery codes, which
     * are shown exactly once.
     * @throws ResponseStatusException 409 when a factor exists, 400 when the code does not match
     */
    public List<String> confirm(int userId, String pendingSecret, String code) {
        if (factors.find(userId).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A second factor is already set up.");
        TotpSecret secret;
        try {
            secret = TotpSecret.of(pendingSecret);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Start the setup again.");
        }
        OptionalLong step = Totp.verify(secret, code, clock.instant());
        if (step.isEmpty()) {
            log.warn("Second-factor setup for user {} rejected an invalid code", userId);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The code is not valid. Check the time on your device and try again.");
        }
        List<String> codes = RecoveryCodes.generate();
        try {
            factors.enrol(userId, cipher.encrypt(secret.base32()), step.getAsLong(), codes.stream().map(cipher::hmac).toList());
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A second factor is already set up.");
        }
        log.info("Second factor enrolled for user {}", userId);
        return codes;
    }

    /** Checks an authenticator code or a recovery code; both are single-use. */
    public Result verify(int userId, String input) {
        if (input == null || input.length() > MAX_INPUT_LENGTH) return failed(userId);
        var factor = factors.find(userId).orElse(null);
        if (factor == null) return failed(userId);
        if (Totp.looksLikeCode(input)) {
            TotpSecret secret = TotpSecret.of(cipher.decrypt(factor.secretEnc()));
            OptionalLong step = Totp.verify(secret, input, clock.instant());
            if (step.isEmpty() || step.getAsLong() <= factor.lastStep()
                    || !factors.advanceStep(userId, factor.lastStep(), step.getAsLong())) return failed(userId);
            return Result.TOTP_OK;
        }
        String code = RecoveryCodes.normalize(input);
        if (code.isEmpty() || !factors.redeemCode(userId, cipher.hmac(code))) return failed(userId);
        log.info("Recovery code used for user {}; {} left", userId, factors.unusedCodes(userId));
        return Result.RECOVERY_CODE_OK;
    }

    public void remove(int userId) {
        if (factors.remove(userId)) log.info("Second factor removed for user {}", userId);
    }

    private static Result failed(int userId) {
        log.warn("Second-factor verification failed for user {}", userId);
        return Result.INVALID;
    }

    Instant now() {
        return clock.instant();
    }
}
