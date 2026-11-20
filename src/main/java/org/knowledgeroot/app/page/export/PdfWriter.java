package org.knowledgeroot.app.page.export;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.jsoup.helper.W3CDom;
import org.jsoup.nodes.DataNode;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Base64;
import java.util.List;

/**
 * PDF output without a browser: the page is laid out as XHTML with an embedded stylesheet and rendered by
 * OpenHTMLtoPDF on PDFBox. The Liberation fonts shipped with the application are embedded (subset), so
 * umlauts and other Unicode text render on every system. Embedded images travel as data URIs; nothing is
 * fetched from the network.
 */
@Component
public class PdfWriter {
    /** Usable width of an A4 page with the margins below, in CSS pixels. */
    static final int MAX_IMAGE_WIDTH = 640;
    static final String SANS = "Liberation Sans";
    static final String MONO = "Liberation Mono";
    private static final String CSS = """
            @page { size: A4; margin: 20mm 18mm 22mm 18mm;
              @bottom-right { content: counter(page) " / " counter(pages); font-family: 'Liberation Sans'; font-size: 8pt; color: #888888; } }
            body { font-family: 'Liberation Sans', sans-serif; font-size: 10.5pt; line-height: 1.4; color: #1b1b1b; }
            h1.kr-title { font-size: 20pt; font-weight: bold; margin: 0 0 6pt 0; color: #1f3864; }
            p.kr-meta { font-size: 8.5pt; color: #666666; margin: 0 0 8pt 0; }
            hr.kr-rule { border: 0; border-top: 1px solid #bbbbbb; margin: 0 0 12pt 0; }
            .kr-body h1 { font-size: 16pt; margin: 14pt 0 6pt 0; color: #1f3864; }
            .kr-body h2 { font-size: 13.5pt; margin: 12pt 0 5pt 0; color: #2f5496; }
            .kr-body h3 { font-size: 12pt; margin: 10pt 0 4pt 0; color: #2f5496; }
            .kr-body h4, .kr-body h5, .kr-body h6 { font-size: 11pt; margin: 8pt 0 4pt 0; }
            .kr-body p { margin: 0 0 7pt 0; }
            .kr-body ul, .kr-body ol { margin: 0 0 7pt 0; padding-left: 18pt; }
            .kr-body li { margin: 2pt 0; }
            .kr-body table { border-collapse: collapse; width: 100%; margin: 0 0 9pt 0; }
            .kr-body th, .kr-body td { border: 1px solid #bbbbbb; padding: 3pt 5pt; vertical-align: top; text-align: left; }
            .kr-body th { background: #eeeeee; font-weight: bold; }
            .kr-body tr { page-break-inside: avoid; }
            .kr-body code, .kr-body pre { font-family: 'Liberation Mono', monospace; font-size: 9pt; }
            .kr-body code { background: #f1f1f1; padding: 0 2pt; }
            .kr-body pre { background: #f4f4f4; border: 1px solid #dddddd; padding: 5pt 6pt; white-space: pre-wrap; word-wrap: break-word; margin: 0 0 9pt 0; }
            .kr-body blockquote { border-left: 2pt solid #cccccc; margin: 0 0 9pt 0; padding: 0 0 0 8pt; color: #555555; }
            .kr-body img { max-width: 100%; }
            .kr-body a { color: #1a4f8a; text-decoration: underline; }
            .kr-body hr { border: 0; border-top: 1px solid #cccccc; margin: 9pt 0; }
            .kr-body figure { margin: 0 0 9pt 0; }
            .kr-body figcaption { font-size: 9pt; color: #666666; font-style: italic; }
            .kr-body mark { background: #fff2a8; }
            .kr-body em.kr-omitted { color: #777777; }
            """;

    public byte[] write(ExportDocument document, ExportText text) {
        Document html = Document.createShell("");
        html.head().appendElement("meta").attr("charset", "utf-8");
        html.head().appendElement("title").text(document.title());
        html.head().appendElement("style").appendChild(new DataNode(CSS));
        Element body = html.body();
        body.appendElement("h1").addClass("kr-title").text(document.title());
        Element meta = body.appendElement("p").addClass("kr-meta");
        boolean first = true;
        for (String line : text.metaLines(document)) {
            if (!first) meta.appendElement("br");
            meta.appendText(line);
            first = false;
        }
        body.appendElement("hr").addClass("kr-rule");
        Element content = body.appendElement("div").addClass("kr-body");
        Element copy = document.body().clone();
        for (Element img : copy.select("img")) {
            ExportImage image = document.image(img);
            if (image == null) {
                Element note = new Element("em").addClass("kr-omitted").text(text.imageOmitted(ExportDocument.describe(img)));
                img.replaceWith(note);
                continue;
            }
            int[] size = image.fit(MAX_IMAGE_WIDTH);
            img.attr("src", "data:" + image.mediaType() + ";base64," + Base64.getEncoder().encodeToString(image.bytes()));
            img.attr("width", String.valueOf(size[0])).attr("height", String.valueOf(size[1]));
            img.removeAttr(ExportDocument.IMAGE_ATTRIBUTE);
        }
        for (Node child : List.copyOf(copy.childNodes())) content.appendChild(child);

        org.w3c.dom.Document w3c = new W3CDom().fromJsoup(html);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withProducer("Knowledgeroot");
            fonts(builder);
            builder.withW3cDocument(w3c, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static void fonts(PdfRendererBuilder builder) {
        builder.useFont(() -> font("LiberationSans-Regular.ttf"), SANS, 400, BaseRendererBuilder.FontStyle.NORMAL, true);
        builder.useFont(() -> font("LiberationSans-Bold.ttf"), SANS, 700, BaseRendererBuilder.FontStyle.NORMAL, true);
        builder.useFont(() -> font("LiberationSans-Italic.ttf"), SANS, 400, BaseRendererBuilder.FontStyle.ITALIC, true);
        builder.useFont(() -> font("LiberationSans-BoldItalic.ttf"), SANS, 700, BaseRendererBuilder.FontStyle.ITALIC, true);
        builder.useFont(() -> font("LiberationMono-Regular.ttf"), MONO, 400, BaseRendererBuilder.FontStyle.NORMAL, true);
    }

    private static InputStream font(String file) {
        InputStream in = PdfWriter.class.getResourceAsStream("/fonts/" + file);
        if (in == null) throw new IllegalStateException("Font missing from the application: " + file);
        return in;
    }
}
