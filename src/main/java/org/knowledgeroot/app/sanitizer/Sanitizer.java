package org.knowledgeroot.app.sanitizer;

import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;

/**
 * Sanitizer class to sanitize input
 */
public class Sanitizer {
    /**
     * Sanitize html input
     *
     * @param input
     * @return cleaned output
     */
    public static String sanitize(String input) {
        /*
        PolicyFactory policy = new HtmlPolicyBuilder()
                .allowElements("a")
                .allowUrlProtocols("https")
                .allowAttributes("href").onElements("a")
                .requireRelNofollowOnLinks()
                .toFactory();
        */

        // create a policy that allows only simple sanitize, links, and images
        PolicyFactory sanitizer = Sanitizers.FORMATTING
                .and(Sanitizers.BLOCKS)
                .and(Sanitizers.IMAGES)
                .and(Sanitizers.LINKS)
                .and(Sanitizers.STYLES)
                .and(Sanitizers.TABLES)
                ;

        // sanitize input
        return sanitizer.sanitize(input);
    }
}
