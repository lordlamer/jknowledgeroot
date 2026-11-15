package org.knowledgeroot.app.mcp;

import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

/** Bytes an assistant sent as an attachment, in the shape the upload service expects from a browser. */
record BytesUpload(String name, String contentType, byte[] bytes) implements MultipartFile {
    @Override public String getName() { return "file"; }
    @Override public String getOriginalFilename() { return name; }
    @Override public String getContentType() { return contentType; }
    @Override public boolean isEmpty() { return bytes.length == 0; }
    @Override public long getSize() { return bytes.length; }
    @Override public byte[] getBytes() { return bytes; }
    @Override public InputStream getInputStream() { return new ByteArrayInputStream(bytes); }
    @Override public void transferTo(File destination) throws IOException { Files.write(destination.toPath(), bytes); }
}
