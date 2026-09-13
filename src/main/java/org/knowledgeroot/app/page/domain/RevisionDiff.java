package org.knowledgeroot.app.page.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Whitespace-preserving differences with a bounded comparison matrix. Render as escaped text. */
public final class RevisionDiff {
    private RevisionDiff() {}
    public record Part(String kind, String text) { }
    /** Text projection of already sanitized HTML; HTML differences remain available separately. */
    public static String text(String html) {
        if (html == null) return "";
        return org.springframework.web.util.HtmlUtils.htmlUnescape(html
                .replaceAll("(?i)<(?:br\\b[^>]*|/p|/div|/h[1-6]|/li|/tr)>", "\n")
                .replaceAll("<[^>]*>", ""));
    }
    public static List<Part> compare(String before, String after) {
        before = before == null ? "" : before;
        after = after == null ? "" : after;
        if (before.equals(after)) return List.of(new Part("same", before));
        if (before.length() > 100_000 || after.length() > 100_000) return blocks(before, after);
        var pattern = Pattern.compile("\\s+|\\S+");
        var left = pattern.matcher(before).results().map(java.util.regex.MatchResult::group).toList();
        var right = pattern.matcher(after).results().map(java.util.regex.MatchResult::group).toList();
        if ((long) (left.size() + 1) * (right.size() + 1) > 1_000_000) return blocks(before, after);
        int[][] lengths = new int[left.size() + 1][right.size() + 1];
        for (int i = left.size() - 1; i >= 0; i--) for (int j = right.size() - 1; j >= 0; j--)
            lengths[i][j] = left.get(i).equals(right.get(j)) ? 1 + lengths[i + 1][j + 1]
                    : Math.max(lengths[i + 1][j], lengths[i][j + 1]);
        var parts = new ArrayList<Part>();
        int i = 0, j = 0;
        while (i < left.size() || j < right.size()) {
            if (i < left.size() && j < right.size() && left.get(i).equals(right.get(j))) {
                parts.add(new Part("same", left.get(i++))); j++;
            } else if (i < left.size() && (j == right.size() || lengths[i + 1][j] >= lengths[i][j + 1]))
                parts.add(new Part("removed", left.get(i++)));
            else parts.add(new Part("added", right.get(j++)));
        }
        // Merge adjacent runs so large blocks do not create thousands of HTML elements.
        var merged = new ArrayList<Part>();
        String kind = null; var text = new StringBuilder();
        for (var part : parts) {
            if (!part.kind().equals(kind)) {
                if (kind != null) merged.add(new Part(kind, text.toString()));
                kind = part.kind(); text.setLength(0);
            }
            text.append(part.text());
        }
        if (kind != null) merged.add(new Part(kind, text.toString()));
        return merged;
    }
    private static List<Part> blocks(String before, String after) {
        return List.of(new Part("removed", before), new Part("added", after));
    }
}
