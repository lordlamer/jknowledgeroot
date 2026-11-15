package org.knowledgeroot.app.page.db;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a search term into an InnoDB boolean-mode query (R68): every word of at least
 * {@link #MIN_WORD} characters must occur, as a word or a prefix ({@code +word*}). Operators the
 * user types are not passed through. Terms without a usable word (very short words, punctuation)
 * fall back to the substring search.
 */
public final class SearchTerms {
    /** Default innodb_ft_min_token_size; shorter words are not in the index. */
    public static final int MIN_WORD = 3;
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}_]+");
    private static final int MAX_WORDS = 10;

    private SearchTerms() {}

    /** The boolean-mode query, or null when the term has no indexable word. */
    public static String booleanQuery(String term) {
        if (term == null) return null;
        List<String> words = new ArrayList<>();
        Matcher matcher = WORD.matcher(term.toLowerCase(Locale.ROOT));
        while (matcher.find() && words.size() < MAX_WORDS) {
            String word = matcher.group();
            if (word.length() >= MIN_WORD && !words.contains(word)) words.add(word);
        }
        if (words.isEmpty()) return null;
        var query = new StringBuilder();
        for (String word : words) query.append('+').append(word).append("* ");
        return query.toString().trim();
    }
}
