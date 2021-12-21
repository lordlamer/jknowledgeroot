package org.knowledgeroot.app.auth;

//@Component
//@Slf4j
/*
public class DatabaseAuthentificationProvider implements AuthenticationProvider {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String AUTH_SQL = "select `password` from `user` WHERE BINARY lower(`login`)=?";

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        Authentication userAuth = null;

        String login = authentication.getName();
        String password = authentication.getCredentials().toString();

        String dbPassword = "";
        try {
            dbPassword = jdbcTemplate.queryForObject(AUTH_SQL, new Object[]{login.toLowerCase()}, String.class);
        } catch(EmptyResultDataAccessException e) {
            log.debug("Could not find user in database: {}", login);
        }

        // check login
        // check plain text password with database password
        if (PasswordHasher.verify(password, dbPassword)) {
            log.info("Login success for user: {}", login);

            // build spring auth object for session
            List<GrantedAuthority> grantedAuths = new ArrayList<>();
            grantedAuths.add(new SimpleGrantedAuthority("ROLE_USER"));
            userAuth = new UsernamePasswordAuthenticationToken(login, password, grantedAuths);
        } else {
            log.info("Login failed for user: {}", login);
        }

        return userAuth;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return authentication.equals(UsernamePasswordAuthenticationToken.class);
    }
}
 */
