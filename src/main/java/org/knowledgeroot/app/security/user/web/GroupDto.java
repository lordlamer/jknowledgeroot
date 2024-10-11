package org.knowledgeroot.app.security.user.web;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@AllArgsConstructor
@ToString
class GroupDto {
    private Integer id;
    private String name;
    private String description;
}
