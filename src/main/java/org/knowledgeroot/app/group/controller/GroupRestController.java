package org.knowledgeroot.app.group.controller;

import org.knowledgeroot.app.config.OrikaMapper;
import org.knowledgeroot.app.group.Group;
import org.knowledgeroot.app.group.GroupFilter;
import org.knowledgeroot.app.group.GroupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

@RestController
public class GroupRestController {
    private static final Logger logger = LoggerFactory.getLogger(GroupRestController.class);

    @Autowired
    protected OrikaMapper mapper;

    @Autowired
    protected GroupService groupService;

    private final static String dateFormat = "yyyy-MM-dd'T'HH:mm:ss";

    /**
     * get filtered list of groups
     * @return
     */
    @RequestMapping(value = "/group", method = RequestMethod.GET)
    public ResponseEntity<List<GroupDto>> filterAllGroups(
            @RequestParam(name = "id", required = false) Integer id,
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "description", required = false) String description,

            @RequestParam(name = "time_start.begin", required = false) String timeStartBegin,
            @RequestParam(name = "time_start.end", required = false) String timeStartEnd,
            @RequestParam(name = "time_end.begin", required = false) String timeEndBegin,
            @RequestParam(name = "time_end.end", required = false) String timeEndEnd,
            @RequestParam(name = "active", required = false) Boolean active,
            @RequestParam(name = "created_by", required = false) Integer createdBy,
            @RequestParam(name = "create_date.begin", required = false) String  createDateBegin,
            @RequestParam(name = "create_date.end", required = false) String  createDateEnd,
            @RequestParam(name = "changed_by", required = false) Integer changedBy,
            @RequestParam(name = "change_date.begin", required = false) String changeDateBegin,
            @RequestParam(name = "change_date.end", required = false) String changeDateEnd,
            @RequestParam(name = "deleted", required = false) Boolean deleted,
            @RequestParam(name = "start", required = false) Integer start,
            @RequestParam(name = "limit", required = false) Integer limit
    ) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(dateFormat);

        GroupFilter groupFilter = new GroupFilter();

        // set filter values
        groupFilter.setId(id);
        groupFilter.setName(name);
        groupFilter.setDescription(description);
        groupFilter.setActive(active);
        groupFilter.setCreatedBy(createdBy);
        groupFilter.setChangedBy(changedBy);
        groupFilter.setDeleted(deleted);
        groupFilter.setLimit(limit);
        groupFilter.setStart(start);

        try {
            if (timeStartBegin != null)
                groupFilter.setTimeStartBegin(LocalDateTime.parse(timeStartBegin, formatter));

            if (timeStartEnd != null)
                groupFilter.setTimeStartEnd(LocalDateTime.parse(timeStartEnd, formatter));

            if (timeEndBegin != null)
                groupFilter.setTimeEndBegin(LocalDateTime.parse(timeEndBegin, formatter));

            if (timeEndEnd != null)
                groupFilter.setTimeEndEnd(LocalDateTime.parse(timeEndEnd, formatter));

            if (createDateBegin != null)
                groupFilter.setCreateDateBegin(LocalDateTime.parse(createDateBegin, formatter));

            if (createDateEnd != null)
                groupFilter.setCreateDateEnd(LocalDateTime.parse(createDateEnd, formatter));

            if (changeDateBegin != null)
                groupFilter.setChangeDateBegin(LocalDateTime.parse(changeDateBegin, formatter));

            if (changeDateEnd != null)
                groupFilter.setChangeDateEnd(LocalDateTime.parse(changeDateEnd, formatter));
        } catch(DateTimeParseException e) {
            logger.error("Could not convert date: " + e.getMessage());
        }

        // get filtered group list
        List<Group> groups = groupService.listGroups(groupFilter);

        // map to dto
        List<GroupDto> groupDtos = mapper.mapAsList(groups, GroupDto.class);

        // check for entries
        if(groupDtos.isEmpty()){
            return new ResponseEntity<List<GroupDto>>(HttpStatus.NO_CONTENT);
        }

        return new ResponseEntity<List<GroupDto>>(groupDtos, HttpStatus.OK);
    }

    /**
     * get single group by id
     * @param id
     * @return
     */
    @RequestMapping(value = "/group/{id}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GroupDto> getGroup(@PathVariable("id") long id) {
        Group group = groupService.findById(id);

        GroupDto groupDto = mapper.map(group, GroupDto.class);

        if (groupDto == null) {
            return new ResponseEntity<GroupDto>(HttpStatus.NOT_FOUND);
        }

        return new ResponseEntity<GroupDto>(groupDto, HttpStatus.OK);
    }

    /**
     * create group
     * @param groupDto
     * @param ucBuilder
     * @return
     */
    @RequestMapping(value = "/group", method = RequestMethod.POST)
    public ResponseEntity<Void> createGroup(@RequestBody GroupDto groupDto, UriComponentsBuilder ucBuilder) {

        Group group = mapper.map(groupDto, Group.class);

        if (groupService.isGroupExist(group)) {
            return new ResponseEntity<Void>(HttpStatus.CONFLICT);
        }

        groupService.createGroup(group);

        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(ucBuilder.path("/group/{id}").buildAndExpand(group.getId()).toUri());

        return new ResponseEntity<Void>(headers, HttpStatus.CREATED);
    }

    /**
     * update existing group
     * @param id
     * @param groupDto
     * @return
     */
    @RequestMapping(value = "/group/{id}", method = RequestMethod.PUT)
    public ResponseEntity<GroupDto> updateGroup(@PathVariable("id") long id, @RequestBody GroupDto groupDto) {
        if(id != groupDto.getId()) {
            return new ResponseEntity<GroupDto>(HttpStatus.CONFLICT);
        }

        Group currentGroup = groupService.findById(id);

        if (currentGroup==null) {
            return new ResponseEntity<GroupDto>(HttpStatus.NOT_FOUND);
        }

        currentGroup = mapper.map(groupDto, Group.class);

        groupService.updateGroup(currentGroup);

        return new ResponseEntity<GroupDto>(groupDto, HttpStatus.OK);
    }

    //------------------- Delete a Group --------------------------------------------------------

    /**
     * delete group
     * @param id
     * @return
     */
    @RequestMapping(value = "/group/{id}", method = RequestMethod.DELETE)
    public ResponseEntity<GroupDto> deleteGroup(@PathVariable("id") long id) {
        Group group = groupService.findById(id);

        if (group == null) {
            return new ResponseEntity<GroupDto>(HttpStatus.NOT_FOUND);
        }

        groupService.deleteGroupById(id);

        return new ResponseEntity<GroupDto>(HttpStatus.NO_CONTENT);
    }

    /**
     * delete all groups
     * @return
     */
    @RequestMapping(value = "/group", method = RequestMethod.DELETE)
    public ResponseEntity<GroupDto> deleteAllGroups() {
        groupService.deleteAllGroups();

        return new ResponseEntity<GroupDto>(HttpStatus.NO_CONTENT);
    }
}
