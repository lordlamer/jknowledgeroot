package org.knowledgeroot.app.security.mfa;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Enrolled TOTP factors and their recovery codes ({@code user_second_factor}, {@code user_recovery_code}). */
@Repository
@RequiredArgsConstructor
public class SecondFactorRepository {
    private final JdbcTemplate jdbc;

    public record EnrolledFactor(byte[] secretEnc, long lastStep, LocalDateTime enrolledAt) {}

    public Optional<EnrolledFactor> find(int userId) {
        return jdbc.query("SELECT secret_enc, last_step, enrolled_at FROM user_second_factor WHERE user_id=?",
                (rs, row) -> new EnrolledFactor(rs.getBytes("secret_enc"), rs.getLong("last_step"),
                        rs.getTimestamp("enrolled_at").toLocalDateTime()), userId).stream().findFirst();
    }

    /** Stores a new factor with its recovery code hashes; fails on the primary key when one exists. */
    @Transactional
    public void enrol(int userId, byte[] secretEnc, long lastStep, List<String> codeHashes) {
        jdbc.update("INSERT INTO user_second_factor (user_id, secret_enc, last_step, enrolled_at) VALUES (?, ?, ?, NOW(3))",
                userId, secretEnc, lastStep);
        for (String hash : codeHashes) {
            jdbc.update("INSERT INTO user_recovery_code (user_id, code_hash) VALUES (?, ?)", userId, hash);
        }
    }

    /** Records the accepted time step; false when another request already moved past {@code previous}. */
    public boolean advanceStep(int userId, long previous, long next) {
        return jdbc.update("UPDATE user_second_factor SET last_step=? WHERE user_id=? AND last_step=?", next, userId, previous) == 1;
    }

    /** Marks a recovery code used; false when it is unknown or was used before. */
    public boolean redeemCode(int userId, String codeHash) {
        return jdbc.update("UPDATE user_recovery_code SET used_at=NOW(3) WHERE user_id=? AND code_hash=? AND used_at IS NULL",
                userId, codeHash) == 1;
    }

    public int unusedCodes(int userId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM user_recovery_code WHERE user_id=? AND used_at IS NULL", Integer.class, userId);
    }

    /** Removes the factor and, through the foreign key, its recovery codes. */
    public boolean remove(int userId) {
        return jdbc.update("DELETE FROM user_second_factor WHERE user_id=?", userId) == 1;
    }
}
