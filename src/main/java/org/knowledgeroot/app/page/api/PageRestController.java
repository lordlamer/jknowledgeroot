package org.knowledgeroot.app.page.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.knowledgeroot.app.page.domain.Page;
import org.knowledgeroot.app.page.domain.PageDao;
import org.knowledgeroot.app.page.domain.PageFilter;
import org.knowledgeroot.app.page.domain.PageId;
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
@RequiredArgsConstructor
public class PageRestController {
    private final PageDao pageImpl;

    private static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";

    private final PageDtoConverter pageDtoConverter = new PageDtoConverter();

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
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_FORMAT);

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
        pageFilter.setLimit(limit);
        pageFilter.setStart(start);

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
            log.error("Could not convert date: {}", e.getMessage());
        }

        // get filtered user list
        List<Page> pages = pageImpl.listPages(pageFilter);

        // map to dto
        List<PageDto> pageDtos = pageDtoConverter.convertAtoB(pages);

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
        Page page = pageImpl.findById(new PageId(id));

        PageDto pageDto = pageDtoConverter.convertAtoB(page);

        if (pageDto == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        return new ResponseEntity<>(pageDto, HttpStatus.OK);
    }

    /**
     * create page
     * @param pageDto page to create
     * @param ucBuilder uri component builder
     */
    @RequestMapping(value = "/page", method = RequestMethod.POST)
    public ResponseEntity<Void> createPage(@RequestBody PageDto pageDto, UriComponentsBuilder ucBuilder) {

        Page page = pageDtoConverter.convertBtoA(pageDto);

        pageImpl.createPage(page);

        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(ucBuilder.path("/page/{id}").buildAndExpand(page.getPageId().value()).toUri());

        return new ResponseEntity<>(headers, HttpStatus.CREATED);
    }

    /**
     * update existing page
     * @param id page id
     * @param pageDto page object to update
     */
    @RequestMapping(value = "/page/{id}", method = RequestMethod.PUT)
    public ResponseEntity<PageDto> updatePage(@PathVariable("id") Integer id, @RequestBody PageDto pageDto) {
        if(id != pageDto.getId()) {
            return new ResponseEntity<>(HttpStatus.CONFLICT);
        }

        Page currentPage = pageImpl.findById(new PageId(id));

        if (currentPage==null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        currentPage = pageDtoConverter.convertBtoA(pageDto);

        pageImpl.updatePage(currentPage);

        return new ResponseEntity<>(pageDto, HttpStatus.OK);
    }

    /**
     * delete page
     * @param id page id
     */
    @RequestMapping(value = "/page/{id}", method = RequestMethod.DELETE)
    public ResponseEntity<PageDto> deletePage(@PathVariable("id") Integer id) {
        Page page = pageImpl.findById(new PageId(id));

        if (page == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        pageImpl.deletePageById(new PageId(id));

        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    /**
     * delete all pages
     */
    @RequestMapping(value = "/page", method = RequestMethod.DELETE)
    public ResponseEntity<PageDto> deleteAllPages() {
        pageImpl.deleteAllPages();

        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}
