package org.knowledgeroot.app.group.mapper;

import ma.glasnost.orika.CustomMapper;
import ma.glasnost.orika.MappingContext;
import org.knowledgeroot.app.group.Group;
import org.knowledgeroot.app.group.impl.database.GroupEntity;
import org.springframework.stereotype.Component;

@Component
public class GroupToGroupEntityMapper extends CustomMapper<Group, GroupEntity> {
    @Override
    public void mapAtoB(Group group, GroupEntity groupEntity, MappingContext context) {
        super.mapAtoB(group, groupEntity, context);
    }

    @Override
    public void mapBtoA(GroupEntity groupEntity, Group group, MappingContext context) {
        super.mapBtoA(groupEntity, group, context);
    }
}
