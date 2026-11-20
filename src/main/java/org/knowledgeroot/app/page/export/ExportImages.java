package org.knowledgeroot.app.page.export;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.knowledgeroot.app.file.domain.File;
import org.knowledgeroot.app.file.domain.FileDao;
import org.knowledgeroot.app.page.domain.PageId;
import org.knowledgeroot.app.page.domain.PagePermission;
import org.knowledgeroot.app.page.domain.PagePermissionDao;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads the attachment behind an {@code img} address for an export. Only attachments of this
 * installation are embedded, and only when the viewer may see the page they belong to; external
 * addresses are never fetched.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExportImages {
    /** The download addresses the editor and the client API use for attachments. */
    static final Pattern ATTACHMENT = Pattern.compile(
            "^/(?:ui/file/(\\d{1,9})/download/[^?#]*|api/v1/files/(\\d{1,9})/content)(?:[?#].*)?$");
    static final int MAX_BYTES = 10 * 1024 * 1024;
    static final Set<String> TYPES = Set.of("image/png", "image/jpeg", "image/gif", "image/bmp");

    private final FileDao files;
    private final PagePermissionDao permissions;

    public Optional<ExportImage> load(String src, Integer viewer) {
        if (src == null) return Optional.empty();
        Matcher matcher = ATTACHMENT.matcher(src.trim());
        if (!matcher.matches()) return Optional.empty();
        int id = Integer.parseInt(matcher.group(1) != null ? matcher.group(1) : matcher.group(2));
        File meta;
        try {
            meta = files.findById(id);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
        if (meta == null || Boolean.TRUE.equals(meta.getDeleted()) || meta.getPageId() == null) return Optional.empty();
        if (!permissions.hasUserPermission(new PageId(meta.getPageId()), viewer, PagePermission.PermissionLevel.VIEW)) return Optional.empty();
        String type = meta.getType() == null ? "" : meta.getType().toLowerCase(Locale.ROOT).split(";")[0].trim();
        if (!TYPES.contains(type)) return Optional.empty();
        if (meta.getSize() != null && meta.getSize() > MAX_BYTES) return Optional.empty();
        try (InputStream in = files.loadFile(id)) {
            if (in == null) return Optional.empty();
            byte[] bytes = in.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) return Optional.empty();
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) return Optional.empty();
            return Optional.of(new ExportImage(bytes, type, meta.getName(), image.getWidth(), image.getHeight()));
        } catch (IOException | RuntimeException ex) {
            log.warn("Attachment {} is not embedded in the export: {}", id, ex.getMessage());
            return Optional.empty();
        }
    }
}
