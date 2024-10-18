package org.knowledgeroot.app.security.user.db;

import org.knowledgeroot.app.security.user.domain.User;
import org.knowledgeroot.app.security.user.domain.UserId;
import org.knowledgeroot.app.util.Converter;

/**
 * Converter for User to UserEntity and vice versa
 */
class UserEntityConverter implements Converter<User, UserEntity> {

    @Override
    public UserEntity convertAtoB(User from) {
        UserEntity userEntity = new UserEntity();

        if(from.getId() != null) {
            userEntity.setId(from.getId().value());
        }

        userEntity.setFirstName(from.getFirstName());
        userEntity.setLastName(from.getLastName());
        userEntity.setLogin(from.getLogin());
        userEntity.setEmail(from.getEmail());
        userEntity.setPassword(from.getPassword());
        userEntity.setActive(from.getActive());
        userEntity.setLanguage(from.getLanguage());
        userEntity.setTimezone(from.getTimezone());
        userEntity.setCreatedBy(from.getCreatedBy());
        userEntity.setCreateDate(from.getCreateDate());
        userEntity.setChangedBy(from.getChangedBy());
        userEntity.setChangeDate(from.getChangeDate());
        userEntity.setDeleted(from.getDeleted());

        return userEntity;
    }

    @Override
    public User convertBtoA(UserEntity from) {
        return User.builder()
                .id(new UserId(from.getId()))
                .firstName(from.getFirstName())
                .lastName(from.getLastName())
                .login(from.getLogin())
                .email(from.getEmail())
                .password(from.getPassword())
                .active(from.getActive())
                .language(from.getLanguage())
                .timezone(from.getTimezone())
                .createdBy(from.getCreatedBy())
                .createDate(from.getCreateDate())
                .changedBy(from.getChangedBy())
                .changeDate(from.getChangeDate())
                .deleted(from.getDeleted())
                .build();
    }
}
