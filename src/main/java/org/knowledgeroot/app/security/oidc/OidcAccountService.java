package org.knowledgeroot.app.security.oidc;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.knowledgeroot.app.i18n.Languages;
import org.knowledgeroot.app.security.auth.PasswordService;
import org.knowledgeroot.app.security.context.domain.UserContext;
import org.knowledgeroot.app.security.context.domain.UserDetails;
import org.knowledgeroot.app.security.context.domain.UserNotFoundException;
import org.knowledgeroot.app.security.recovery.RecoverySettings;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Maps a signed-in OpenID Connect user to a Knowledgeroot account (R64): the identity (issuer and
 * subject) is linked once, either to the account with the same verified e-mail address or to a new
 * account, and group memberships and the admin flag follow the configured claims on every sign-in.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OidcAccountService {
    private static final Pattern LOGIN_CHARS = Pattern.compile("[^a-z0-9._@-]+");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcClient jdbc;
    private final PasswordService passwords;
    private final UserContext users;
    private final OidcSettings settings;

    /** A sign-in the account mapping refuses; {@code code} names the reason for the log. */
    @Getter
    public static class Rejected extends RuntimeException {
        private final String code;

        Rejected(String code, String message) {
            super(message);
            this.code = code;
        }
    }

    /** The external identity linked to an account. */
    public record Identity(int userId, String issuer, String subject, String email,
                           LocalDateTime createDate, LocalDateTime lastLogin) {}

    @Transactional
    public UserDetails signIn(OidcUser oidc) {
        String issuer = String.valueOf(oidc.getIssuer());
        String subject = oidc.getSubject();
        if (subject == null || subject.isBlank() || subject.length() > 255 || issuer.length() > 255)
            throw new Rejected("subject", "The identity token has no usable subject");
        String email = RecoverySettings.email(oidc.getEmail());
        boolean emailVerified = email != null && !Boolean.FALSE.equals(oidc.getEmailVerified());

        var linked = jdbc.sql("""
                        SELECT ui.user_id, ui.groups FROM user_identity ui JOIN user u ON u.id = ui.user_id
                        WHERE ui.issuer = :issuer AND ui.subject = :subject AND u.deleted = 0""")
                .param("issuer", issuer).param("subject", subject)
                .query((rs, row) -> new Object[]{rs.getInt("user_id"), rs.getString("groups")}).optional();
        Integer userId;
        String previousGroups = null;
        if (linked.isPresent()) {
            userId = (Integer) linked.get()[0];
            previousGroups = (String) linked.get()[1];
        } else {
            if (oidc.getEmail() != null && !emailVerified)
                throw new Rejected("email-unverified", "The provider has not verified the e-mail address " + oidc.getEmail());
            userId = emailVerified ? byEmail(email, issuer) : null;
            if (userId == null) userId = create(oidc, email);
            jdbc.sql("""
                            INSERT INTO user_identity (user_id, issuer, subject, email, groups, create_date, last_login)
                            VALUES (:user, :issuer, :subject, :email, NULL, NOW(3), NOW(3))""")
                    .param("user", userId).param("issuer", issuer).param("subject", subject).param("email", email).update();
            log.info("OpenID Connect identity {} at {} linked to user id {}", subject, issuer, userId);
        }

        List<String> groups = claimGroups(oidc);
        if (groups != null) {
            syncGroups(userId, groups, previousGroups);
            if (!settings.getAdminGroup().isEmpty()) {
                boolean admin = groups.contains(settings.getAdminGroup());
                jdbc.sql("UPDATE user SET admin = :admin WHERE id = :id").param("admin", admin).param("id", userId).update();
            }
        }
        jdbc.sql("UPDATE user_identity SET last_login = NOW(3), email = :email, groups = :groups WHERE issuer = :issuer AND subject = :subject")
                .param("email", email).param("groups", groups == null ? previousGroups : String.join("\n", groups))
                .param("issuer", issuer).param("subject", subject).update();

        try {
            UserDetails details = users.getUserContextForId(String.valueOf(userId));
            if (details.isGuest()) throw new Rejected("inactive", "The account is not active");
            return details;
        } catch (UserNotFoundException ex) {
            throw new Rejected("inactive", "The account is not active");
        }
    }

    /** The identity linked to an account, for the administration. */
    public Optional<Identity> identityOf(int userId) {
        return jdbc.sql("SELECT user_id, issuer, subject, email, create_date, last_login FROM user_identity WHERE user_id = :id ORDER BY last_login DESC LIMIT 1")
                .param("id", userId)
                .query((rs, row) -> new Identity(rs.getInt("user_id"), rs.getString("issuer"), rs.getString("subject"), rs.getString("email"),
                        rs.getTimestamp("create_date").toLocalDateTime(), rs.getTimestamp("last_login").toLocalDateTime()))
                .optional();
    }

    /** Ids of the accounts that sign in through the provider, for the user list. */
    public Set<Integer> linkedUserIds() {
        return new HashSet<>(jdbc.sql("SELECT DISTINCT user_id FROM user_identity").query(Integer.class).list());
    }

    private Integer byEmail(String email, String issuer) {
        var matches = jdbc.sql("SELECT id FROM user WHERE LOWER(email) = :email AND deleted = 0").param("email", email).query(Integer.class).list();
        if (matches.isEmpty()) return null;
        if (matches.size() > 1) throw new Rejected("email-ambiguous", "Several accounts use the e-mail address " + email);
        int userId = matches.getFirst();
        int others = jdbc.sql("SELECT COUNT(*) FROM user_identity WHERE user_id = :id AND issuer = :issuer")
                .param("id", userId).param("issuer", issuer).query(Integer.class).single();
        if (others > 0) throw new Rejected("email-taken", "The account with the e-mail address " + email + " is already linked to another identity of this provider");
        return userId;
    }

    private int create(OidcUser oidc, String email) {
        String login = uniqueLogin(loginCandidate(oidc, email));
        String[] names = names(oidc, login);
        byte[] secret = new byte[32];
        RANDOM.nextBytes(secret);
        var keys = new GeneratedKeyHolder();
        jdbc.sql("""
                        INSERT INTO user (first_name, last_name, login, email, password, admin, language, timezone, time_start, time_end,
                                          active, created_by, create_date, changed_by, change_date, deleted, local_login)
                        VALUES (:first, :last, :login, :email, :password, FALSE, :language, 'UTC', NOW(), NOW(),
                                TRUE, 0, NOW(), 0, NOW(), FALSE, FALSE)""")
                .param("first", names[0]).param("last", names[1]).param("login", login).param("email", email == null ? "" : email)
                .param("password", passwords.encode(Base64.getUrlEncoder().withoutPadding().encodeToString(secret)))
                .param("language", language(oidc.getLocale()))
                .update(keys);
        int id = keys.getKey().intValue();
        jdbc.sql("UPDATE user SET created_by = :id, changed_by = :id WHERE id = :id").param("id", id).update();
        log.info("Account {} (id {}) created from an OpenID Connect sign-in", login, id);
        return id;
    }

    static String loginCandidate(OidcUser oidc, String email) {
        for (String candidate : new String[]{oidc.getPreferredUsername(), email, oidc.getEmail()}) {
            if (candidate == null) continue;
            String cleaned = LOGIN_CHARS.matcher(candidate.strip().toLowerCase(Locale.ROOT)).replaceAll("-")
                    .replaceAll("^[-.]+|[-.]+$", "");
            if (cleaned.length() >= 3) return cleaned.length() > 100 ? cleaned.substring(0, 100) : cleaned;
        }
        return "sso-" + Long.toHexString(RANDOM.nextLong() & 0xffffffffL);
    }

    private String uniqueLogin(String candidate) {
        if (!loginExists(candidate)) return candidate;
        String base = candidate.length() > 96 ? candidate.substring(0, 96) : candidate;
        for (int i = 2; i < 100; i++) {
            String attempt = base + "-" + i;
            if (!loginExists(attempt)) return attempt;
        }
        return base.substring(0, Math.min(base.length(), 80)) + "-" + Long.toHexString(RANDOM.nextLong() & 0xffffffffL);
    }

    private boolean loginExists(String login) {
        return jdbc.sql("SELECT COUNT(*) FROM user WHERE BINARY LOWER(login) = :login").param("login", login).query(Integer.class).single() > 0;
    }

    static String[] names(OidcUser oidc, String login) {
        String first = text(oidc.getGivenName()), last = text(oidc.getFamilyName());
        if (first.isEmpty() && last.isEmpty()) {
            String full = text(oidc.getFullName());
            int space = full.lastIndexOf(' ');
            if (space > 0) { first = full.substring(0, space).strip(); last = full.substring(space + 1).strip(); }
            else if (!full.isEmpty()) first = full;
            else first = login;
        }
        return new String[]{first, last};
    }

    private static String text(String value) {
        if (value == null) return "";
        String stripped = value.strip();
        return stripped.length() > 255 ? stripped.substring(0, 255) : stripped;
    }

    static String language(String locale) {
        if (locale == null || locale.isBlank()) return Languages.normalise(null);
        try {
            return Languages.normalise(locale.strip().split("[-_]")[0]);
        } catch (IllegalArgumentException ex) {
            return Languages.normalise(null);
        }
    }

    /** The groups named by the configured claim, or null when the claim is not configured or absent. */
    private List<String> claimGroups(OidcUser oidc) {
        if (settings.getGroupsClaim().isEmpty()) return null;
        Object raw = oidc.getClaims().get(settings.getGroupsClaim());
        if (raw == null) return null;
        var names = new LinkedHashSet<String>();
        Collection<?> values = raw instanceof Collection<?> collection ? collection
                : raw.toString().contains(",") ? List.of(raw.toString().split(",")) : List.of(raw.toString());
        for (Object value : values) {
            if (value == null) continue;
            String name = value.toString().strip();
            // Keycloak may send the full group path ("/parent/child"); the group itself is the last segment.
            if (name.startsWith("/")) name = name.substring(name.lastIndexOf('/') + 1);
            if (!name.isEmpty() && name.length() <= 255) names.add(name);
        }
        return new ArrayList<>(names);
    }

    private void syncGroups(int userId, List<String> wanted, String previous) {
        Set<String> before = previous == null || previous.isBlank() ? Set.of() : new HashSet<>(List.of(previous.split("\n")));
        for (String name : wanted) {
            Integer groupId = groupId(name);
            if (groupId == null && settings.isCreateGroups()) groupId = createGroup(name, userId);
            if (groupId == null) {
                log.debug("OpenID Connect group {} has no local group and is skipped", name);
                continue;
            }
            int members = jdbc.sql("SELECT COUNT(*) FROM group_member WHERE group_id = :group AND member_id = :user AND member_type = 'user'")
                    .param("group", groupId).param("user", userId).query(Integer.class).single();
            if (members == 0) {
                jdbc.sql("INSERT INTO group_member (group_id, member_id, member_type) VALUES (:group, :user, 'user')")
                        .param("group", groupId).param("user", userId).update();
            }
        }
        for (String name : before) {
            if (wanted.contains(name)) continue;
            Integer groupId = groupId(name);
            if (groupId != null) {
                jdbc.sql("DELETE FROM group_member WHERE group_id = :group AND member_id = :user AND member_type = 'user'")
                        .param("group", groupId).param("user", userId).update();
            }
        }
    }

    private Integer groupId(String name) {
        return jdbc.sql("SELECT id FROM `group` WHERE LOWER(name) = LOWER(:name) AND deleted = 0 ORDER BY id LIMIT 1")
                .param("name", name).query(Integer.class).optional().orElse(null);
    }

    private int createGroup(String name, int actor) {
        var keys = new GeneratedKeyHolder();
        jdbc.sql("""
                        INSERT INTO `group` (name, description, active, created_by, create_date, changed_by, change_date, deleted)
                        VALUES (:name, 'Created from single sign-on', TRUE, :actor, NOW(), :actor, NOW(), FALSE)""")
                .param("name", name).param("actor", actor).update(keys);
        log.info("Group {} created from an OpenID Connect sign-in", name);
        return keys.getKey().intValue();
    }
}
