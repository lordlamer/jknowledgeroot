package org.knowledgeroot.app.security.user.api.converter;

import org.knowledgeroot.app.security.user.api.dto.GroupDto;
import org.knowledgeroot.app.security.user.domain.Group;
import org.knowledgeroot.app.security.user.domain.GroupId;
import org.knowledgeroot.app.util.Converter;

public class GroupDtoConverter implements Converter<Group, GroupDto> {

    @Override
    public GroupDto convertAtoB(Group from) {
        GroupDto groupDto = new GroupDto();

        groupDto.setId(from.getId().value());
        groupDto.setName(from.getName());
        groupDto.setDescription(from.getDescription());
        groupDto.setCreatedBy(from.getCreatedBy());
        groupDto.setCreateDate(from.getCreateDate());
        groupDto.setChangedBy(from.getChangedBy());
        groupDto.setChangeDate(from.getCreateDate());
        groupDto.setTimeStart(from.getTimeStart());
        groupDto.setTimeEnd(from.getTimeEnd());
        groupDto.setDeleted(from.getDeleted());
        groupDto.setActive(from.getActive());

        return groupDto;
    }

    @Override
    public Group convertBtoA(GroupDto from) {
        Group group = new Group();

        group.setId(new GroupId(from.getId()));
        group.setName(from.getName());
        group.setDescription(from.getDescription());
        group.setCreatedBy(from.getCreatedBy());
        group.setCreateDate(from.getCreateDate());
        group.setChangedBy(from.getChangedBy());
        group.setChangeDate(from.getCreateDate());
        group.setTimeStart(from.getTimeStart());
        group.setTimeEnd(from.getTimeEnd());
        group.setDeleted(from.getDeleted());
        group.setActive(from.getActive());

        return group;
    }
}
