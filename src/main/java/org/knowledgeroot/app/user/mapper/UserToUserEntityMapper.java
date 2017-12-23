package org.knowledgeroot.app.user.mapper;

import ma.glasnost.orika.CustomMapper;
import ma.glasnost.orika.MappingContext;
import org.knowledgeroot.app.user.User;
import org.knowledgeroot.app.user.impl.database.UserEntity;
import org.springframework.stereotype.Component;

@Component
public class UserToUserEntityMapper extends CustomMapper<User, UserEntity> {
    @Override
    public void mapAtoB(User user, UserEntity userEntity, MappingContext context) {
        super.mapAtoB(user, userEntity, context);
    }

    @Override
    public void mapBtoA(UserEntity userEntity, User user, MappingContext context) {
        super.mapBtoA(userEntity, user, context);
    }
}
