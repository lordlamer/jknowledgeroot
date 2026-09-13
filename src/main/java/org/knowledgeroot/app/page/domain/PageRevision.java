package org.knowledgeroot.app.page.domain;

import java.time.LocalDateTime;
import java.util.List;

public record PageRevision(int id, long revision, String name, String content,
                           LocalDateTime changedAt, boolean labelsCaptured, List<String> labels) { }
