package org.knowledgeroot.app.user.controller;

import org.knowledgeroot.app.config.OrikaMapper;
import org.knowledgeroot.app.user.User;
import org.knowledgeroot.app.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

@RestController
public class UserRestController {
    @Autowired
    protected OrikaMapper mapper;

    @Autowired
    protected UserService userService;

    //-------------------Retrieve All Users--------------------------------------------------------

    @RequestMapping(value = "/user/", method = RequestMethod.GET)
    public ResponseEntity<List<UserDto>> listAllUsers() {
        List<User> users = userService.findAllUsers();

        List<UserDto> userDtos = mapper.mapAsList(users, UserDto.class);

        if(userDtos.isEmpty()){
            return new ResponseEntity<List<UserDto>>(HttpStatus.NO_CONTENT);
        }

        return new ResponseEntity<List<UserDto>>(userDtos, HttpStatus.OK);
    }


    //-------------------Retrieve Single User--------------------------------------------------------

    @RequestMapping(value = "/user/{id}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UserDto> getUser(@PathVariable("id") long id) {
        User user = userService.findById(id);

        UserDto userDto = mapper.map(user, UserDto.class);

        if (userDto == null) {
            return new ResponseEntity<UserDto>(HttpStatus.NOT_FOUND);
        }

        return new ResponseEntity<UserDto>(userDto, HttpStatus.OK);
    }


    //-------------------Create a User--------------------------------------------------------

    @RequestMapping(value = "/user/", method = RequestMethod.POST)
    public ResponseEntity<Void> createUser(@RequestBody UserDto userDto, UriComponentsBuilder ucBuilder) {

        User user = mapper.map(userDto, User.class);

        if (userService.isUserExist(user)) {
            return new ResponseEntity<Void>(HttpStatus.CONFLICT);
        }

        userService.saveUser(user);

        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(ucBuilder.path("/user/{id}").buildAndExpand(user.getId()).toUri());

        return new ResponseEntity<Void>(headers, HttpStatus.CREATED);
    }


    //------------------- Update a User --------------------------------------------------------

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

    @RequestMapping(value = "/user/{id}", method = RequestMethod.DELETE)
    public ResponseEntity<UserDto> deleteUser(@PathVariable("id") long id) {
        User user = userService.findById(id);

        if (user == null) {
            return new ResponseEntity<UserDto>(HttpStatus.NOT_FOUND);
        }

        userService.deleteUserById(id);

        return new ResponseEntity<UserDto>(HttpStatus.NO_CONTENT);
    }


    //------------------- Delete All Users --------------------------------------------------------

    @RequestMapping(value = "/user/", method = RequestMethod.DELETE)
    public ResponseEntity<UserDto> deleteAllUsers() {
        userService.deleteAllUsers();

        return new ResponseEntity<UserDto>(HttpStatus.NO_CONTENT);
    }
}
