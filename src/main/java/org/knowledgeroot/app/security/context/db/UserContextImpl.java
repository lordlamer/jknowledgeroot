package org.knowledgeroot.app.security.context.db;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.knowledgeroot.app.security.context.domain.UserContextDao;
import org.knowledgeroot.app.security.context.domain.UserDetails;
import org.knowledgeroot.app.security.context.domain.UserNotFoundException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.knowledgeroot.app.security.auth.PasswordService;

import java.util.Locale;

@Component
@RequiredArgsConstructor
@Slf4j
class UserContextImpl implements UserContextDao {
    private final JdbcClient jdbcClient;

    @Override
    public UserDetails getUserDetails(String name) {
        return findUser(jdbcClient.sql("SELECT * FROM user WHERE BINARY LOWER(login) = :login AND active = 1 AND deleted = 0")
                .param("login", name.toLowerCase(Locale.ROOT)));
    }

    @Override
    public UserDetails getUserDetailsById(String userId) {
        return findUser(jdbcClient.sql("SELECT * FROM user WHERE id = :id AND active = 1 AND deleted = 0")
                .param("id", userId));
    }

    private UserDetails findUser(JdbcClient.StatementSpec query) {
        return query
                .query((rs, rowNum) -> UserDetails.builder()
                        .userId(rs.getString("id"))
                        .credentialTag(PasswordService.credentialTag(rs.getString("password")))
                        .login(rs.getString("login"))
                        .email(rs.getString("email"))
                        .firstName(rs.getString("first_name"))
                        .surName(rs.getString("last_name"))
                        .role(rs.getBoolean("admin") ? UserDetails.Role.ADMIN : UserDetails.Role.USER)
                        .language(rs.getString("language"))
                        .build()
                ).optional().orElseThrow(() -> new UserNotFoundException("User not found"));
    }
}
