package org.knowledgeroot.app.services;

import lombok.AllArgsConstructor;
import org.knowledgeroot.app.api.filter.GroupFilter;
import org.knowledgeroot.persistence.Group.Group;
import org.knowledgeroot.domain.GroupDao;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class GroupService {
    private final GroupDao groupImpl;

    /**
     * find groups by filter
     * @return
     */
    public List<Group> listGroups(GroupFilter groupFilter) {
        return groupImpl.listGroups(groupFilter);
    }

    /**
     * find group by given id
     * @param id
     * @return
     */
    public Group findById(long id) {
        return groupImpl.findById(id);
    }

    /**
     * check if group exists
     * @param group
     * @return
     */
    public boolean isGroupExist(Group group) {
        return groupImpl.isGroupExist(group);
    }

    /**
     * create group
     * @param group
     */
    public void createGroup(Group group) {
        groupImpl.createGroup(group);
    }

    /**
     * update existing group
     * @param group
     */
    public void updateGroup(Group group) {
        groupImpl.updateGroup(group);
    }

    /**
     * delete all groups
     */
    public void deleteAllGroups() {
        groupImpl.deleteAllGroups();
    }

    /**
     * delete group by id
     * @param id
     */
    public void deleteGroupById(long id) {
        groupImpl.deleteGroupById(id);
    }
}
