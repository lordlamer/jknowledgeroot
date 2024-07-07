package org.knowledgeroot.app.profile.ui;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
class UpdateProfileDto {
    private String login;
    private String email;
    private String firstName;
    private String surName;
}
