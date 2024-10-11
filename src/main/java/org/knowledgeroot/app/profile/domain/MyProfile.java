package org.knowledgeroot.app.profile.domain;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder
@ToString
public class MyProfile {
    private String login;
    private String email;
    private String firstName;
    private String surName;
}
