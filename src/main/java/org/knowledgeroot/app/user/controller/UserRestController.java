package org.knowledgeroot.app.user.controller;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.knowledgeroot.app.config.OrikaMapper;
import org.knowledgeroot.app.user.User;
import org.knowledgeroot.app.user.UserFilter;
import org.knowledgeroot.app.user.UserService;
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
public class UserRestController {
    private final OrikaMapper mapper;
    private final UserService userService;

    private final static String dateFormat = "yyyy-MM-dd'T'HH:mm:ss";

    /**
     * get all users
     * @return
     */
    @RequestMapping(value = "/user", method = RequestMethod.GET)
    public ResponseEntity<List<UserDto>> listAllUsers(
            @RequestParam(name = "id", required = false) Integer id,
            @RequestParam(name = "first_name", required = false) String firstName,
            @RequestParam(name = "last_name", required = false) String lastName,
            @RequestParam(name = "login", required = false) String login,
            @RequestParam(name = "email", required = false) String email,
            @RequestParam(name = "language", required = false) String language,
            @RequestParam(name = "timezone", required = false) String timezone,

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

        UserFilter userFilter = new UserFilter();

        // set filter values
        userFilter.setId(id);
        userFilter.setFirstName(firstName);
        userFilter.setLastName(lastName);
        userFilter.setLogin(login);
        userFilter.setEmail(email);
        userFilter.setLanguage(language);
        userFilter.setTimezone(timezone);
        userFilter.setActive(active);
        userFilter.setCreatedBy(createdBy);
        userFilter.setChangedBy(changedBy);
        userFilter.setDeleted(deleted);
        userFilter.setLimit(limit);
        userFilter.setStart(start);

        try {
            if (timeStartBegin != null)
                userFilter.setTimeStartBegin(LocalDateTime.parse(timeStartBegin, formatter));

            if (timeStartEnd != null)
                userFilter.setTimeStartEnd(LocalDateTime.parse(timeStartEnd, formatter));

            if (timeEndBegin != null)
                userFilter.setTimeEndBegin(LocalDateTime.parse(timeEndBegin, formatter));

            if (timeEndEnd != null)
                userFilter.setTimeEndEnd(LocalDateTime.parse(timeEndEnd, formatter));

            if (createDateBegin != null)
                userFilter.setCreateDateBegin(LocalDateTime.parse(createDateBegin, formatter));

            if (createDateEnd != null)
                userFilter.setCreateDateEnd(LocalDateTime.parse(createDateEnd, formatter));

            if (changeDateBegin != null)
                userFilter.setChangeDateBegin(LocalDateTime.parse(changeDateBegin, formatter));

            if (changeDateEnd != null)
                userFilter.setChangeDateEnd(LocalDateTime.parse(changeDateEnd, formatter));
        } catch(DateTimeParseException e) {
            log.error("Could not convert date: " + e.getMessage());
        }

        // get filtered user list
        List<User> users = userService.listUsers(userFilter);

        // map to dto
        List<UserDto> userDtos = mapper.mapAsList(users, UserDto.class);

        // check for entries
        if(userDtos.isEmpty()){
            return new ResponseEntity<List<UserDto>>(HttpStatus.NO_CONTENT);
        }

        return new ResponseEntity<List<UserDto>>(userDtos, HttpStatus.OK);
    }

    /**
     * get single user by id
     * @param id
     * @return
     */
    @RequestMapping(value = "/user/{id}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UserDto> getUser(@PathVariable("id") long id) {
        User user = userService.findById(id);

        UserDto userDto = mapper.map(user, UserDto.class);

        if (userDto == null) {
            return new ResponseEntity<UserDto>(HttpStatus.NOT_FOUND);
        }

        return new ResponseEntity<UserDto>(userDto, HttpStatus.OK);
    }

    /**
     * create user
     * @param userDto
     * @param ucBuilder
     * @return
     */
    @RequestMapping(value = "/user", method = RequestMethod.POST)
    public ResponseEntity<Void> createUser(@RequestBody UserDto userDto, UriComponentsBuilder ucBuilder) {

        User user = mapper.map(userDto, User.class);

        if (userService.isUserExist(user)) {
            return new ResponseEntity<Void>(HttpStatus.CONFLICT);
        }

        userService.createUser(user);

        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(ucBuilder.path("/user/{id}").buildAndExpand(user.getId()).toUri());

        return new ResponseEntity<Void>(headers, HttpStatus.CREATED);
    }

    /**
     * update existing user
     * @param id
     * @param userDto
     * @return
     */
    @RequestMapping(value = "/user/{id}", method = RequestMethod.PUT)
    public ResponseEntity<UserDto> updateUser(@PathVariable("id") long id, @RequestBody UserDto userDto) {
        if(id != userDto.getId()) {
            return new ResponseEntity<UserDto>(HttpStatus.CONFLICT);
        }

        User currentUser = userService.findById(id);

        if (currentUser==null) {
            return new ResponseEntity<UserDto>(HttpStatus.NOT_FOUND);
        }

        currentUser = mapper.map(userDto, User.class);

        userService.updateUser(currentUser);

        return new ResponseEntity<UserDto>(userDto, HttpStatus.OK);
    }

    //------------------- Delete a User --------------------------------------------------------

    /**
     * delete user
     * @param id
     * @return
     */
    @RequestMapping(value = "/user/{id}", method = RequestMethod.DELETE)
    public ResponseEntity<UserDto> deleteUser(@PathVariable("id") long id) {
        User user = userService.findById(id);

        if (user == null) {
            return new ResponseEntity<UserDto>(HttpStatus.NOT_FOUND);
        }

        userService.deleteUserById(id);

        return new ResponseEntity<UserDto>(HttpStatus.NO_CONTENT);
    }

    /**
     * delete all users
     * @return
     */
    @RequestMapping(value = "/user", method = RequestMethod.DELETE)
    public ResponseEntity<UserDto> deleteAllUsers() {
        userService.deleteAllUsers();

        return new ResponseEntity<UserDto>(HttpStatus.NO_CONTENT);
    }
}
