package org.knowledgeroot.app.profile.db;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.knowledgeroot.app.profile.domain.MyProfile;
import org.knowledgeroot.app.profile.domain.MyProfileDao;
import org.knowledgeroot.app.security.context.domain.UserContext;
import org.knowledgeroot.app.security.context.domain.UserDetails;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
class MyProfileImpl implements MyProfileDao {
    private final JdbcClient jdbcClient;
    private final UserContext userContext;

    /**
     * find my profile
     * @return
     */
    @Override
    public MyProfile findMyProfile() {
        UserDetails userDetails = userContext.getUserContext();

        return jdbcClient.sql("SELECT login, email, first_name, last_name FROM user WHERE id = ?")
                .params(userDetails.getUserId())
                .query(rs -> {
                    if(rs.next()) {
                        return MyProfile.builder()
                                .login(rs.getString("login"))
                                .email(rs.getString("email"))
                                .firstName(rs.getString("first_name"))
                                .surName(rs.getString("last_name"))
                                .build();
                    }

                    return null;
                });
    }

    /**
     * update my profile
     * @param myProfile
     */
    @Override
    public void updateMyProfile(MyProfile myProfile) {
        UserDetails userDetails = userContext.getUserContext();

        if(userDetails.isGuest()) {
            throw new IllegalStateException("Guest user can't update profile");
        }

        jdbcClient
                .sql("UPDATE user SET email = ?, first_name = ?, last_name = ?, login = ? WHERE id = ?")
                .params(
                        myProfile.getEmail(),
                        myProfile.getFirstName(),
                        myProfile.getSurName(),
                        myProfile.getLogin(),
                        userDetails.getUserId()
                )
                .update();
    }
}
