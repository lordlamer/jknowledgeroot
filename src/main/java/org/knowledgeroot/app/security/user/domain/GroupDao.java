package org.knowledgeroot.app.security.user.domain;

import org.knowledgeroot.app.security.user.api.filter.GroupFilter;
import org.knowledgeroot.app.security.user.db.Group;

import java.util.List;

public interface GroupDao {
    /**
     * find groups
     * @return
     */
    List<org.knowledgeroot.app.security.user.db.Group> listGroups(GroupFilter groupFilter);

    /**
     * find group by given id
     * @param id
     * @return
     */
    org.knowledgeroot.app.security.user.db.Group findById(long id);

    /**
     * check if group exists
     * @param group
     * @return
     */
    boolean isGroupExist(org.knowledgeroot.app.security.user.db.Group group);

    /**
     * create group from domain object
     * @param group
     */
    void createGroup(org.knowledgeroot.app.security.user.db.Group group);

    /**
     * update existing group
     * @param group
     */
    void updateGroup(Group group);

    /**
     * delete all groups
     */
    void deleteAllGroups();

    /**
     * delete group by given id
     * @param id
     */
    void deleteGroupById(long id);
}
