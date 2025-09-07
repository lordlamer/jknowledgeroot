package org.knowledgeroot.app.security.user.web;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@AllArgsConstructor
@ToString
class UserDto {
    private String firstName;
    private String lastName;
    private String login;
    private String email;
    private String password;
    private Boolean admin;
}
