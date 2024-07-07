package org.knowledgeroot.app.profile.domain;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MyProfile {
    private String login;
    private String email;
    private String firstName;
    private String surName;
}
