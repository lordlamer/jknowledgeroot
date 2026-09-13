package org.knowledgeroot.app.page.api;

import lombok.RequiredArgsConstructor;
import org.knowledgeroot.app.util.RequestValidation;
import lombok.extern.slf4j.Slf4j;
import org.knowledgeroot.app.page.domain.Page;
import org.knowledgeroot.app.page.domain.PageCreationService;
import org.knowledgeroot.app.page.domain.PageEditingService;
import org.knowledgeroot.app.page.domain.PageDao;
import org.knowledgeroot.app.page.domain.PageFilter;
import org.knowledgeroot.app.page.domain.PageId;
import org.knowledgeroot.app.page.domain.PagePermission;
import org.knowledgeroot.app.page.domain.PagePermissionDao;
import org.knowledgeroot.app.security.context.domain.UserContext;
import org.knowledgeroot.app.security.context.domain.UserDetails;
import org.springframework.dao.EmptyResultDataAccessException;
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
import java.util.Objects;

@RestController
@Slf4j
@RequiredArgsConstructor
public class PageRestController {
    private final PageDao pageImpl;
    private final PagePermissionDao pagePermissionDao;
    private final UserContext userContext;
    private final PageCreationService pageCreationService;
    private final PageEditingService pageEditingService;

    private static final String DATE_FORMAT = "uuuu-MM-dd'T'HH:mm:ss";

    private final PageDtoConverter pageDtoConverter = new PageDtoConverter();

    private Integer getCurrentUserId() {
        UserDetails user = userContext.getUserContext();
        if (user == null || user.isGuest()) {
            return null;
        }
        try {
            return Integer.valueOf(user.getUserId());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean canView(PageId pageId, Integer userId) {
        return pagePermissionDao.hasUserPermission(pageId, userId, PagePermission.PermissionLevel.VIEW);
    }

    private PageDto toVisibleDto(Page page, Integer userId) {
        PageDto dto = pageDtoConverter.convertAtoB(page);
        // Reading a child does not grant access to its parent or expose its ID.
        if (dto.getParent() != null && dto.getParent() > 0
                && !canView(new PageId(dto.getParent()), userId)) {
            dto.setParent(null);
        }
        return dto;
    }

    /**
     * get all pages
     */
    @RequestMapping(value = "/page", method = RequestMethod.GET)
    public ResponseEntity<List<PageDto>> listAllPages(
            @RequestParam(name = "id", required = false) Integer id,
            @RequestParam(name = "parent", required = false) Integer parent,
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "content", required = false) String content,

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
        RequestValidation.id(id);
        RequestValidation.require(parent == null || parent >= 0);
        RequestValidation.text(name, 255);
        RequestValidation.text(content, 200);
        Integer userId = getCurrentUserId();
        if (id != null && !canView(new PageId(id), userId)) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_FORMAT).withResolverStyle(java.time.format.ResolverStyle.STRICT);

        PageFilter pageFilter = new PageFilter();

        // set filter values
        pageFilter.setId(id);
        pageFilter.setParent(parent);
        pageFilter.setName(name);
        pageFilter.setContent(content);

        pageFilter.setActive(active);
        pageFilter.setCreatedBy(createdBy);
        pageFilter.setChangedBy(changedBy);
        pageFilter.setDeleted(deleted);
        pageFilter.setLimit(RequestValidation.limit(limit));
        pageFilter.setStart(RequestValidation.start(start));

        try {
            if (timeStartBegin != null)
                pageFilter.setTimeStartBegin(LocalDateTime.parse(timeStartBegin, formatter));

            if (timeStartEnd != null)
                pageFilter.setTimeStartEnd(LocalDateTime.parse(timeStartEnd, formatter));

            if (timeEndBegin != null)
                pageFilter.setTimeEndBegin(LocalDateTime.parse(timeEndBegin, formatter));

            if (timeEndEnd != null)
                pageFilter.setTimeEndEnd(LocalDateTime.parse(timeEndEnd, formatter));

            if (createDateBegin != null)
                pageFilter.setCreateDateBegin(LocalDateTime.parse(createDateBegin, formatter));

            if (createDateEnd != null)
                pageFilter.setCreateDateEnd(LocalDateTime.parse(createDateEnd, formatter));

            if (changeDateBegin != null)
                pageFilter.setChangeDateBegin(LocalDateTime.parse(changeDateBegin, formatter));

            if (changeDateEnd != null)
                pageFilter.setChangeDateEnd(LocalDateTime.parse(changeDateEnd, formatter));
        } catch(DateTimeParseException e) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date");
        }

        RequestValidation.range(pageFilter.getCreateDateBegin(), pageFilter.getCreateDateEnd());
        RequestValidation.range(pageFilter.getChangeDateBegin(), pageFilter.getChangeDateEnd());
        RequestValidation.range(pageFilter.getTimeStartBegin(), pageFilter.getTimeStartEnd());
        RequestValidation.range(pageFilter.getTimeEndBegin(), pageFilter.getTimeEndEnd());

        // Apply object permissions before exposing any page data.
        List<Page> pages = pageImpl.listVisiblePages(pageFilter, userId);

        List<PageDto> pageDtos = pages.stream()
                .map(pageDtoConverter::convertAtoB)
                .toList();

        // check for entries
        if(pageDtos.isEmpty()){
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        }

        return new ResponseEntity<>(pageDtos, HttpStatus.OK);
    }

    /**
     * get single page by id
     * @param id page id
     */
    @RequestMapping(value = "/page/{id}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PageDto> getPage(@PathVariable("id") Integer id) {
        PageId pageId = new PageId(id);
        Integer userId = getCurrentUserId();
        if (!canView(pageId, userId)) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }

        Page page;
        try {
            page = pageImpl.findById(pageId);
        } catch (EmptyResultDataAccessException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        if (page == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        return new ResponseEntity<>(toVisibleDto(page, userId), HttpStatus.OK);
    }

    /**
     * create page
     * @param pageDto page to create
     * @param ucBuilder uri component builder
     */
    @RequestMapping(value = "/page", method = RequestMethod.POST)
    public ResponseEntity<Void> createPage(@RequestBody PageDto pageDto, UriComponentsBuilder ucBuilder) {

        int id = pageCreationService.create(pageDto, List.of());

        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(ucBuilder.path("/page/{id}").buildAndExpand(id).toUri());

        return new ResponseEntity<>(headers, HttpStatus.CREATED);
    }

    /**
     * update existing page
     * @param id page id
     * @param pageDto page object to update
     */
    @RequestMapping(value = "/page/{id}", method = RequestMethod.PUT)
    public ResponseEntity<PageDto> updatePage(@PathVariable("id") Integer id, @RequestBody PageDto pageDto) {
        if(!Objects.equals(id, pageDto.getId())) {
            return new ResponseEntity<>(HttpStatus.CONFLICT);
        }

        return ResponseEntity.ok(pageEditingService.update(new PageId(id), pageDto));
    }
    /**
     * delete page
     * @param id page id
     */
    @RequestMapping(value = "/page/{id}", method = RequestMethod.DELETE)
    public ResponseEntity<PageDto> deletePage(@PathVariable("id") Integer id,
                                             @RequestParam(required = false) Long revision) {
        pageEditingService.delete(new PageId(id), revision);

        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    /**
     * delete all pages
     */
    @RequestMapping(value = "/page", method = RequestMethod.DELETE)
    public ResponseEntity<PageDto> deleteAllPages() {
        return new ResponseEntity<>(HttpStatus.METHOD_NOT_ALLOWED);
    }
}
