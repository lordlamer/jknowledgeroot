package org.knowledgeroot.app.profile.ui;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@Builder
@ToString
class UpdateProfileDto {
    private String login;
    private String email;
    private String firstName;
    private String surName;
}
