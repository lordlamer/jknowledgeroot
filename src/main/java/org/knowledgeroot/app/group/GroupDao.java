package org.knowledgeroot.app.group;

import org.knowledgeroot.app.group.impl.database.Group;

import java.util.List;

public interface GroupDao {
    /**
     * find groups
     * @return
     */
    List<Group> listGroups(GroupFilter groupFilter);

    /**
     * find group by given id
     * @param id
     * @return
     */
    Group findById(long id);

    /**
     * check if group exists
     * @param group
     * @return
     */
    boolean isGroupExist(Group group);

    /**
     * create group from domain object
     * @param group
     */
    void createGroup(Group group);

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
