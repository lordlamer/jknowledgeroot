package org.knowledgeroot.app.security.user.db;

import org.knowledgeroot.app.security.user.domain.Group;
import org.knowledgeroot.app.security.user.domain.GroupId;
import org.knowledgeroot.app.util.Converter;

/**
 * Converter for Group to GroupEntity and vice versa
 */
class GroupEntityConverter implements Converter<Group, GroupEntity> {

    @Override
    public GroupEntity convertAtoB(Group from) {
        GroupEntity groupEntity = new GroupEntity();

        if(from.getId() != null) {
            groupEntity.setId(from.getId().value());
        }

        groupEntity.setName(from.getName());
        groupEntity.setDescription(from.getDescription());
        groupEntity.setActive(from.getActive());
        groupEntity.setCreatedBy(from.getCreatedBy());
        groupEntity.setCreateDate(from.getCreateDate());
        groupEntity.setChangedBy(from.getChangedBy());
        groupEntity.setChangeDate(from.getChangeDate());
        groupEntity.setDeleted(from.getDeleted());

        return groupEntity;
    }

    @Override
    public Group convertBtoA(GroupEntity from) {
        return Group.builder()
                .id(new GroupId(from.getId()))
                .name(from.getName())
                .description(from.getDescription())
                .active(from.getActive())
                .createdBy(from.getCreatedBy())
                .createDate(from.getCreateDate())
                .changedBy(from.getChangedBy())
                .changeDate(from.getChangeDate())
                .deleted(from.getDeleted())
                .build();
    }
}
