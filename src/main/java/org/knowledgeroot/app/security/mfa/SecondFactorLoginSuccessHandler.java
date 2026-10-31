package org.knowledgeroot.app.security.mfa;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.knowledgeroot.app.security.context.domain.UserDetails;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import java.io.IOException;

/** After the password: either finish the sign-in as before or park the session until the second factor. */
public class SecondFactorLoginSuccessHandler implements AuthenticationSuccessHandler {
    static final String SUCCESS_URL = "/login/success";
    static final String MFA_URL = "/mfa";
    private final SecondFactorGate gate;

    /** @param gate null disables the second step (test contexts without the MFA beans) */
    public SecondFactorLoginSuccessHandler(SecondFactorGate gate) {
        this.gate = gate;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        var requirement = SecondFactorGate.Requirement.NONE;
        if (gate != null && authentication.getDetails() instanceof UserDetails user) {
            requirement = gate.requirement(user);
            if (requirement != SecondFactorGate.Requirement.NONE) gate.storePending(user, requirement, request, response);
        }
        response.sendRedirect(request.getContextPath() + (requirement == SecondFactorGate.Requirement.NONE ? SUCCESS_URL : MFA_URL));
    }
}
