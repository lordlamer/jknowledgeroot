package org.knowledgeroot.app.user.controller;

import org.knowledgeroot.app.config.OrikaMapper;
import org.knowledgeroot.app.user.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
public class UserRestController {
    @Autowired
    protected OrikaMapper mapper;

    @RequestMapping("/test")
    public UserDto test() {
        User user = new User();
        UserDto userDto = new UserDto();

        user.setEmail("me@la.de");
        user.setId(1);
        user.setActive(true);
        user.setFirstName("foo");
        user.setLastName("bar");
        user.setLogin("foobar");
        user.setLanguage("xxx");

        userDto = mapper.map(user, UserDto.class);

        return userDto;
    }
}
