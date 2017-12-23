package org.knowledgeroot.app.group.mapper;

import ma.glasnost.orika.CustomMapper;
import ma.glasnost.orika.MappingContext;
import org.knowledgeroot.app.group.Group;
import org.knowledgeroot.app.group.controller.GroupDto;
import org.springframework.stereotype.Component;

@Component
public class GroupToGroupDtoMapper extends CustomMapper<Group, GroupDto> {
    @Override
    public void mapAtoB(Group group, GroupDto groupDto, MappingContext context) {
        super.mapAtoB(group, groupDto, context);
    }

    @Override
    public void mapBtoA(GroupDto groupDto, Group group, MappingContext context) {
        super.mapBtoA(groupDto, group, context);
    }
}
