package org.knowledgeroot.app.file.api;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.knowledgeroot.app.file.domain.File;
import org.knowledgeroot.app.file.domain.FileDao;
import org.knowledgeroot.app.file.domain.FileFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@Slf4j
@RequiredArgsConstructor
class FileRestController {
    private final FileDao fileImpl;

    private final static String dateFormat = "yyyy-MM-dd'T'HH:mm:ss";

    private final FileDtoConverter fileDtoConverter = new FileDtoConverter();

    /**
     * get all files
     */
    @RequestMapping(value = "/file", method = RequestMethod.GET)
    public ResponseEntity<List<FileDto>> listAllFiles(
            @RequestParam(name = "id", required = false) Integer id,
            @RequestParam(name = "page_id", required = false) Integer pageId,


            @RequestParam(name = "time_start.begin", required = false) String timeStartBegin,
            @RequestParam(name = "time_start.end", required = false) String timeStartEnd,
            @RequestParam(name = "time_end.begin", required = false) String timeEndBegin,
            @RequestParam(name = "time_end.end", required = false) String timeEndEnd,
            @RequestParam(name = "active", required = false) Boolean active,
            @RequestParam(name = "created_by", required = false) Integer createdBy,
            @RequestParam(name = "create_date.begin", required = false) String  createDateBegin,
            @RequestParam(name = "create_date.end", required = false) String  createDateEnd,
            @RequestParam(name = "changed_by", required = false) Integer changedBy,
            @RequestParam(name = "change_date.begin", required = false) String changeDateBegin,
            @RequestParam(name = "change_date.end", required = false) String changeDateEnd,
            @RequestParam(name = "deleted", required = false) Boolean deleted,
            @RequestParam(name = "start", required = false) Integer start,
            @RequestParam(name = "limit", required = false) Integer limit
    ) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(dateFormat);

        FileFilter fileFilter = new FileFilter();

        // set filter values


        // get filtered user list
        List<File> files = fileImpl.listFiles(fileFilter);

        // map to dto
        List<FileDto> fileDtos = fileDtoConverter.convertAtoB(files);

        // check for entries
        if(fileDtos.isEmpty()){
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        }

        return new ResponseEntity<>(fileDtos, HttpStatus.OK);
    }

    /**
     * get single file meta data by id
     * @param id file id
     */
    @RequestMapping(value = "/file/{id}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<FileDto> getFile(@PathVariable("id") long id) {
        File file = fileImpl.findById(id);

        FileDto fileDto = fileDtoConverter.convertAtoB(file);

        if (fileDto == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        return new ResponseEntity<>(fileDto, HttpStatus.OK);
    }

    /**
     * download file
     *
     * @param response http response
     * @param fileId file id
     * @param filename file name
     */
    @RequestMapping(value="download/{id}/{filename}", method = RequestMethod.GET)
    public void downloadFile(
            HttpServletResponse response,
            @PathVariable("id") Integer fileId,
            @PathVariable("filename") String filename
    ) {
        try {
            // load file meta informations
            File meta = fileImpl.findById(fileId);

            // load file from storage
            java.io.File file = new java.io.File("foo"); // test only
            //java.io.File file = storageService.loadFile(fileId);

            // prepare response
            // set mime type
            response.setContentType(meta.getType());

            // set filename
            response.setHeader(
                    "Content-Disposition",
                    String.format("attachment; filename=\"" + meta.getName() +"\"")
            );

            // set content length
            response.setContentLength((int)file.length());

            // push file content
            InputStream inputStream = new BufferedInputStream(new FileInputStream(file));
            FileCopyUtils.copy(inputStream, response.getOutputStream());
        } catch (Exception e) {
            log.error(e.getMessage());

            try {
                // show error message to user
                String errorMessage = "Could not download file!";
                OutputStream outputStream = response.getOutputStream();
                outputStream.write(errorMessage.getBytes(StandardCharsets.UTF_8));
                outputStream.close();
            } catch (Exception ex) {
                log.error(ex.getMessage());
            }
        }
    }

    /**
     * create file
     *
     * @param files multipart files
     * @param parentContent parent content id
     * @param ucBuilder uri component builder
     */
    @RequestMapping(value = "/file", method = RequestMethod.POST)
    public ResponseEntity<Void> createFile(
            @RequestParam("file") MultipartFile[] files,
            @RequestParam Integer parentContent,
            UriComponentsBuilder ucBuilder
    ) {
        // create all files
        for(MultipartFile file : files) {
            /*
            if (fileService.isFileExist(file)) {
                return new ResponseEntity<Void>(HttpStatus.CONFLICT);
            }
            */

            fileImpl.createFile(file, parentContent);
        }

        HttpHeaders headers = new HttpHeaders();

        /*
        if(files.length == 1)
            headers.setLocation(ucBuilder.path("/file/{id}").buildAndExpand(file.getId()).toUri());
        */

        return new ResponseEntity<>(headers, HttpStatus.CREATED);
    }

    /**
     * delete file by id
     * @param id file id
     */
    @RequestMapping(value = "/file/{id}", method = RequestMethod.DELETE)
    public ResponseEntity<FileDto> deleteFile(@PathVariable("id") long id) {
        File file = fileImpl.findById(id);

        if (file == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        fileImpl.deleteFileById(id);

        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    /**
     * delete all files
     */
    @RequestMapping(value = "/file", method = RequestMethod.DELETE)
    public ResponseEntity<FileDto> deleteAllFiles() {
        fileImpl.deleteAllFiles();

        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}
