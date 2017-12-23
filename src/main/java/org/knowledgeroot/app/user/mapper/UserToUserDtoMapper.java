package org.knowledgeroot.app.user.mapper;

import ma.glasnost.orika.CustomMapper;
import ma.glasnost.orika.MappingContext;
import org.knowledgeroot.app.user.User;
import org.knowledgeroot.app.user.controller.UserDto;
import org.springframework.stereotype.Component;

@Component
public class UserToUserDtoMapper extends CustomMapper<User, UserDto> {
    @Override
    public void mapAtoB(User user, UserDto userDto, MappingContext context) {
        super.mapAtoB(user, userDto, context);
    }

    @Override
    public void mapBtoA(UserDto userDto, User user, MappingContext context) {
        super.mapBtoA(userDto, user, context);
    }
}
