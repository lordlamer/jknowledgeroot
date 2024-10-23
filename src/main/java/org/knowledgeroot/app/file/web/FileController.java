package org.knowledgeroot.app.file.web;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.knowledgeroot.app.file.domain.File;
import org.knowledgeroot.app.file.domain.FileDao;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Controller
@RequiredArgsConstructor
@Slf4j
class FileController {
    private final FileDao fileDao;

    @PostMapping(
            value = "/ui/file",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public @ResponseBody String uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("contentId") Integer contentId) {
        try {
            if (file.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is empty");
            }

            fileDao.createFile(file, contentId);

            return "File uploaded successfully: " + file.getOriginalFilename() + " Filesize: " + file.getSize();
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not upload file: " + e.getMessage()
            );
        }
    }

    @GetMapping("/ui/file/{id}/download/{filename}")
    public void downloadFile(
            HttpServletResponse response,
            @PathVariable("id") Integer fileId,
            @PathVariable("filename") String filename
    ) {
        try {
            // Lade die Dateimetadaten
            File meta = fileDao.findById(fileId);

            // Datei-Inhalt direkt über den InputStream laden
            InputStream inputStream = fileDao.loadFile(fileId);

            // Überprüfen, ob der InputStream null ist (Datei existiert möglicherweise nicht)
            if (inputStream == null) {
                response.setStatus(HttpStatus.NOT_FOUND.value());
                response.getWriter().write("File not found");
                return;
            }

            // Antwort vorbereiten
            // Mime-Typ setzen
            response.setContentType(meta.getType());

            // Dateiname im Header setzen
            response.setHeader(
                    "Content-Disposition",
                    String.format("attachment; filename=\"%s\"", meta.getName())
            );

            // Den Inhalt in die Antwort schreiben
            FileCopyUtils.copy(inputStream, response.getOutputStream());

            // InputStream schließen
            inputStream.close();
        } catch (Exception e) {
            log.error("Fehler beim Herunterladen der Datei: " + e.getMessage(), e);

            try {
                // Fehlermeldung an den Benutzer senden
                String errorMessage = "Could not download file!";
                response.setContentType("text/plain");
                response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
                response.getOutputStream().write(errorMessage.getBytes(StandardCharsets.UTF_8));
                response.getOutputStream().close();
            } catch (Exception ex) {
                log.error("Fehler beim Senden der Fehlermeldung: " + ex.getMessage(), ex);
            }
        }
    }
}
