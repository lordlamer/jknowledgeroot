package org.knowledgeroot.app.security.user.api.converter;

import org.knowledgeroot.app.security.user.api.dto.UserDto;
import org.knowledgeroot.app.security.user.domain.User;
import org.knowledgeroot.app.security.user.domain.UserId;
import org.knowledgeroot.app.util.Converter;

public class UserDtoConverter implements Converter<User, UserDto> {

    @Override
    public UserDto convertAtoB(User from) {
        UserDto userDto = new UserDto();

        userDto.setId(from.getId().value());
        userDto.setActive(from.getActive());
        userDto.setEmail(from.getEmail());
        userDto.setLogin(from.getLogin());
        userDto.setFirstName(from.getFirstName());
        userDto.setLastName(from.getLastName());
        userDto.setCreatedBy(from.getCreatedBy());
        userDto.setCreateDate(from.getCreateDate());
        userDto.setChangedBy(from.getChangedBy());
        userDto.setChangeDate(from.getChangeDate());
        userDto.setLanguage(from.getLanguage());
        userDto.setDeleted(from.getDeleted());
        userDto.setPassword("***");
        userDto.setTimezone(from.getTimezone());
        userDto.setTimeStart(from.getTimeStart());
        userDto.setTimeEnd(from.getTimeEnd());

        return userDto;
    }

    @Override
    public User convertBtoA(UserDto from) {
        User user = new User();

        user.setId(new UserId(from.getId()));
        user.setActive(from.getActive());
        user.setEmail(from.getEmail());
        user.setLogin(from.getLogin());
        user.setFirstName(from.getFirstName());
        user.setLastName(from.getLastName());
        user.setCreatedBy(from.getCreatedBy());
        user.setCreateDate(from.getCreateDate());
        user.setChangedBy(from.getChangedBy());
        user.setChangeDate(from.getChangeDate());
        user.setLanguage(from.getLanguage());
        user.setDeleted(from.getDeleted());
        user.setPassword(from.getPassword());
        user.setTimezone(from.getTimezone());
        user.setTimeStart(from.getTimeStart());
        user.setTimeEnd(from.getTimeEnd());

        return user;
    }
}
