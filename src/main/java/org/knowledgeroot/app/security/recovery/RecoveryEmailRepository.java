package org.knowledgeroot.app.security.recovery;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;

@Repository
@RequiredArgsConstructor
public class RecoveryEmailRepository {
    private final JdbcTemplate jdbc;

    /** Caller updates the email in the same transaction, while this user row remains locked. */
    @Transactional
    public void invalidateIfChanged(int id, String email) {
        var old = jdbc.queryForObject("SELECT email FROM user WHERE id=? FOR UPDATE", String.class, id);
        if (!Objects.equals(old, email)) {
            jdbc.update("DELETE FROM recovery_token WHERE user_id=?", id);
            jdbc.update("DELETE FROM recovery_email WHERE user_id=?", id);
        }
    }

    public boolean verified(int id) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM recovery_email e JOIN user u ON u.id=e.user_id
                WHERE u.id=? AND BINARY LOWER(TRIM(u.email))=e.email AND u.active=1 AND u.deleted=0
                """, Integer.class, id) == 1;
    }
}
