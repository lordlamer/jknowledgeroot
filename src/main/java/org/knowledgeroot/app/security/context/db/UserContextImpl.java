package org.knowledgeroot.app.security.context.db;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.knowledgeroot.app.security.context.domain.UserContextDao;
import org.knowledgeroot.app.security.context.domain.UserDetails;
import org.knowledgeroot.app.security.context.domain.UserNotFoundException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
class UserContextImpl implements UserContextDao {
    private final JdbcClient jdbcClient;

    @Override
    public UserDetails getUserDetails(String name) {
        return jdbcClient.sql("SELECT * FROM user WHERE login = :login")
                .param("login", name)
                .query((rs, rowNum) -> UserDetails.builder()
                        .userId(rs.getString("id"))
                        .login(rs.getString("login"))
                        .email(rs.getString("email"))
                        .firstName(rs.getString("first_name"))
                        .surName(rs.getString("last_name"))
                        .role(UserDetails.Role.ADMIN)
                        .language(rs.getString("language"))
                        .build()
                ).optional().orElseThrow(() -> new UserNotFoundException("User not found"));
    }
}
