package org.knowledgeroot.app.content.controller;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.knowledgeroot.app.content.ContentFilter;
import org.knowledgeroot.app.content.ContentService;
import org.knowledgeroot.app.content.impl.database.Content;
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
@Slf4j
@AllArgsConstructor
public class ContentRestController {
    private final ContentService contentService;

    private final static String dateFormat = "yyyy-MM-dd'T'HH:mm:ss";

    private final ContentDtoConverter contentDtoConverter = new ContentDtoConverter();

    /**
     * get all contents
     */
    @RequestMapping(value = "/content", method = RequestMethod.GET)
    public ResponseEntity<List<ContentDto>> listAllContents(
            @RequestParam(name = "id", required = false) Integer id,
            @RequestParam(name = "parent", required = false) Integer parent,
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "content", required = false) String content,
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "sorting", required = false) Integer sorting,

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

        ContentFilter contentFilter = new ContentFilter();

        // set filter values
        contentFilter.setId(id);
        contentFilter.setParent(parent);
        contentFilter.setName(name);
        contentFilter.setContent(content);
        contentFilter.setType(type);
        contentFilter.setSorting(sorting);

        contentFilter.setActive(active);
        contentFilter.setCreatedBy(createdBy);
        contentFilter.setChangedBy(changedBy);
        contentFilter.setDeleted(deleted);
        contentFilter.setLimit(limit);
        contentFilter.setStart(start);

        try {
            if (timeStartBegin != null)
                contentFilter.setTimeStartBegin(LocalDateTime.parse(timeStartBegin, formatter));

            if (timeStartEnd != null)
                contentFilter.setTimeStartEnd(LocalDateTime.parse(timeStartEnd, formatter));

            if (timeEndBegin != null)
                contentFilter.setTimeEndBegin(LocalDateTime.parse(timeEndBegin, formatter));

            if (timeEndEnd != null)
                contentFilter.setTimeEndEnd(LocalDateTime.parse(timeEndEnd, formatter));

            if (createDateBegin != null)
                contentFilter.setCreateDateBegin(LocalDateTime.parse(createDateBegin, formatter));

            if (createDateEnd != null)
                contentFilter.setCreateDateEnd(LocalDateTime.parse(createDateEnd, formatter));

            if (changeDateBegin != null)
                contentFilter.setChangeDateBegin(LocalDateTime.parse(changeDateBegin, formatter));

            if (changeDateEnd != null)
                contentFilter.setChangeDateEnd(LocalDateTime.parse(changeDateEnd, formatter));
        } catch(DateTimeParseException e) {
            log.error("Could not convert date: " + e.getMessage());
        }

        // get filtered user list
        List<Content> contents = contentService.listContents(contentFilter);

        // map to dto
        List<ContentDto> contentDtos = contentDtoConverter.fromDomain(contents);

        // check for entries
        if(contentDtos.isEmpty()){
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        }

        return new ResponseEntity<>(contentDtos, HttpStatus.OK);
    }

    /**
     * get single content by id
     * @param id
     */
    @RequestMapping(value = "/content/{id}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ContentDto> getContent(@PathVariable("id") long id) {
        Content content = contentService.findById(id);

        ContentDto contentDto = contentDtoConverter.fromDomain(content);

        if (contentDto == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        return new ResponseEntity<>(contentDto, HttpStatus.OK);
    }

    /**
     * create content
     * @param contentDto content
     * @param ucBuilder uri component builder
     */
    @RequestMapping(value = "/content", method = RequestMethod.POST)
    public ResponseEntity<Void> createContent(@RequestBody ContentDto contentDto, UriComponentsBuilder ucBuilder) {

        Content content = contentDtoConverter.toDomain(contentDto);

        if (contentService.isContentExist(content)) {
            return new ResponseEntity<>(HttpStatus.CONFLICT);
        }

        contentService.createContent(content);

        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(ucBuilder.path("/content/{id}").buildAndExpand(content.getId()).toUri());

        return new ResponseEntity<>(headers, HttpStatus.CREATED);
    }

    /**
     * update existing content
     * @param id content id
     * @param contentDto content
     */
    @RequestMapping(value = "/content/{id}", method = RequestMethod.PUT)
    public ResponseEntity<ContentDto> updateContent(@PathVariable("id") long id, @RequestBody ContentDto contentDto) {
        if(id != contentDto.getId()) {
            return new ResponseEntity<>(HttpStatus.CONFLICT);
        }

        Content currentContent = contentService.findById(id);

        if (currentContent==null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        currentContent = contentDtoConverter.toDomain(contentDto);

        contentService.updateContent(currentContent);

        return new ResponseEntity<>(contentDto, HttpStatus.OK);
    }

    /**
     * delete content
     * @param id content id
     */
    @RequestMapping(value = "/content/{id}", method = RequestMethod.DELETE)
    public ResponseEntity<ContentDto> deleteContent(@PathVariable("id") long id) {
        Content content = contentService.findById(id);

        if (content == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        contentService.deleteContentById(id);

        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    /**
     * delete all contents
     */
    @RequestMapping(value = "/content", method = RequestMethod.DELETE)
    public ResponseEntity<ContentDto> deleteAllContents() {
        contentService.deleteAllContents();

        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}
