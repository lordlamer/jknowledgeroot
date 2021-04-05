package org.knowledgeroot.app.group;

import lombok.Data;
import org.knowledgeroot.app.user.User;

import java.util.List;

@Data
public class GroupMember {
    private Integer id;
    private List<User> users;
    private List<Group> groups;
}
