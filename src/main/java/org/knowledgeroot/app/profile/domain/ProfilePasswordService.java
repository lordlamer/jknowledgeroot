package org.knowledgeroot.app.profile.domain;

import lombok.RequiredArgsConstructor;
import org.knowledgeroot.app.security.auth.CredentialRepository;
import org.knowledgeroot.app.security.auth.LoginAttemptLimiter;
import org.knowledgeroot.app.security.auth.PasswordService;
import org.knowledgeroot.app.security.context.domain.UserContext;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ProfilePasswordService {
    private final UserContext users;
    private final CredentialRepository credentials;
    private final PasswordService passwords;
    private final LoginAttemptLimiter limiter;

    // No enclosing transaction: failed reauthentication must not roll back its quota.
    public void change(String current, String replacement, String confirmation, String remoteAddress) {
        var user = users.getUserContext();
        if (user.isGuest()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        if (replacement == null || !replacement.equals(confirmation)) invalid("The new passwords do not match.");
        if (replacement.equals(current)) invalid("Choose a different new password.");
        if (current == null || current.isEmpty() || current.length() > 1024) invalid("Enter your current password.");
        if (replacement.length() < 16 || replacement.length() > 128 || replacement.isBlank())
            invalid("The new password must contain 16–128 characters.");
        try {
            limiter.requirePasswordChangeAttempt(user.getUserId(), remoteAddress);
        } catch (BadCredentialsException e) {
            invalid("Password change could not be verified. Check your current password or try again later.");
        }
        var credential = credentials.findById(user.getUserId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
        if (!passwords.matches(current, credential.hash()))
            invalid("Password change could not be verified. Check your current password or try again later.");
        if (!credentials.changePassword(user.getUserId(), credential.hash(), passwords.encodeNewPassword(replacement)))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Your account changed. Sign in again before changing the password.");
    }

    private static void invalid(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
