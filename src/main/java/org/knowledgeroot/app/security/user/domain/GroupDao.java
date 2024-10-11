package org.knowledgeroot.app.security.user.domain;

import org.knowledgeroot.app.security.user.api.filter.GroupFilter;

import java.util.List;

/**
 * Group data access object
 */
public interface GroupDao {
    /**
     * find groups by given filter
     * @return list of groups
     */
    List<Group> listGroups(GroupFilter groupFilter);

    /**
     * find group by given id
     * @param groupId group id
     * @return group
     */
    Group findById(GroupId groupId);

    /**
     * check if group exists
     * @param group group object
     * @return true if group exists
     */
    boolean isGroupExist(Group group);

    /**
     * create group from domain object
     * @param group group object
     */
    void createGroup(Group group);

    /**
     * update existing group
     * @param group group object
     */
    void updateGroup(Group group);

    /**
     * delete all groups
     */
    void deleteAllGroups();

    /**
     * delete group by given id
     * @param groupId group id
     */
    void deleteGroupById(GroupId groupId);
}
