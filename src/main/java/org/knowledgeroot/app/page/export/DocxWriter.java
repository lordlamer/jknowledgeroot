package org.knowledgeroot.app.page.export;

import org.apache.poi.common.usermodel.PictureType;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.Borders;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.TableWidthType;
import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.apache.poi.xwpf.usermodel.XWPFAbstractNum;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFNumbering;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFStyles;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTAbstractNum;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFonts;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTInd;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTLvl;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTNumLvl;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPrGeneral;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTShd;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSpacing;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STJc;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STNumberFormat;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STShd;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STStyleType;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Word output (.docx, also opened by LibreOffice Writer) through Apache POI: a title paragraph, the metadata
 * lines, then the page body mapped element by element to paragraphs with named heading styles, numbered
 * and bulleted lists, tables, code blocks, quotes, hyperlinks and embedded pictures.
 */
@Component
public class DocxWriter {
    static final String FONT = "Arial";
    static final String MONO = "Courier New";
    /** Usable width of an A4 page with 2 cm margins, in pixels at 96 dpi. */
    static final int MAX_IMAGE_WIDTH = 600;
    static final int TWIPS_PER_LEVEL = 720;
    private static final Set<String> BLOCKS = Set.of("p", "div", "section", "article", "figure", "figcaption", "header",
            "footer", "main", "aside", "details", "summary", "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "li",
            "blockquote", "pre", "table", "thead", "tbody", "tfoot", "tr", "td", "th", "hr", "address", "nav", "dl", "dt", "dd");
    private static final Pattern RGB = Pattern.compile("rgba?\\(\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})");
    private static final Pattern HEX = Pattern.compile("#([0-9a-fA-F]{6}|[0-9a-fA-F]{3})\\b");
    private static final Map<String, String> NAMED_COLORS = Map.ofEntries(
            Map.entry("red", "FF0000"), Map.entry("green", "008000"), Map.entry("blue", "0000FF"), Map.entry("black", "000000"),
            Map.entry("gray", "808080"), Map.entry("grey", "808080"), Map.entry("orange", "FFA500"), Map.entry("purple", "800080"),
            Map.entry("yellow", "FFFF00"), Map.entry("teal", "008080"), Map.entry("navy", "000080"), Map.entry("maroon", "800000"),
            Map.entry("olive", "808000"), Map.entry("silver", "C0C0C0"), Map.entry("brown", "A52A2A"), Map.entry("white", "FFFFFF"));

    public byte[] write(ExportDocument document, ExportText text) {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            new Writer(doc, document, text).write();
            doc.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /** Where paragraphs go: the document body or a table cell (whose first, empty paragraph is reused). */
    private interface Sink {
        XWPFParagraph paragraph();
    }

    /** Inline formatting collected while walking down the tree. */
    private record Format(boolean bold, boolean italic, boolean underline, boolean strike, boolean code,
                          boolean highlight, String color, String link, String vertical) {
        static final Format PLAIN = new Format(false, false, false, false, false, false, null, null, null);
        Format withBold() { return new Format(true, italic, underline, strike, code, highlight, color, link, vertical); }
        Format withItalic() { return new Format(bold, true, underline, strike, code, highlight, color, link, vertical); }
        Format withUnderline() { return new Format(bold, italic, true, strike, code, highlight, color, link, vertical); }
        Format withStrike() { return new Format(bold, italic, underline, true, code, highlight, color, link, vertical); }
        Format withCode() { return new Format(bold, italic, underline, strike, true, highlight, color, link, vertical); }
        Format withHighlight() { return new Format(bold, italic, underline, strike, code, true, color, link, vertical); }
        Format color(String value) { return value == null ? this : new Format(bold, italic, underline, strike, code, highlight, value, link, vertical); }
        Format link(String href) { return new Format(bold, italic, underline, strike, code, highlight, color, href, vertical); }
        Format vertical(String value) { return new Format(bold, italic, underline, strike, code, highlight, color, link, value); }
    }

    /** Block context: indentation in twips, quote border, table head. */
    private record Context(int indent, boolean quote, boolean head, boolean inCell) {
        static final Context TOP = new Context(0, false, false, false);
        Context quoted() { return new Context(indent + TWIPS_PER_LEVEL, true, head, inCell); }
        Context indented(int levels) { return new Context(TWIPS_PER_LEVEL * levels, quote, head, inCell); }
        Context cell(boolean isHead) { return new Context(0, false, isHead, true); }
    }

    private static final class Writer {
        private final XWPFDocument doc;
        private final ExportDocument document;
        private final ExportText text;
        private XWPFNumbering numbering;
        private BigInteger bulletNum;
        private BigInteger decimalAbstract;
        private int abstractCount;

        Writer(XWPFDocument doc, ExportDocument document, ExportText text) {
            this.doc = doc;
            this.document = document;
            this.text = text;
        }

        void write() {
            styles();
            doc.getProperties().getCoreProperties().setTitle(document.title());
            if (document.changedBy() != null && !document.changedBy().isBlank()) doc.getProperties().getCoreProperties().setCreator(document.changedBy());
            XWPFParagraph title = doc.createParagraph();
            title.setStyle("Title");
            text(title, document.title(), Format.PLAIN);
            XWPFParagraph meta = doc.createParagraph();
            meta.setSpacingAfter(160);
            meta.setBorderBottom(Borders.SINGLE);
            boolean first = true;
            for (String line : text.metaLines(document)) {
                if (!first) meta.createRun().addBreak();
                XWPFRun run = meta.createRun();
                base(run);
                run.setFontSize(9);
                run.setColor("666666");
                run.setText(line);
                first = false;
            }
            blocks(document.body(), doc::createParagraph, Context.TOP);
        }

        // ----- document structure -----

        private void styles() {
            XWPFStyles styles = doc.createStyles();
            CTFonts fonts = CTFonts.Factory.newInstance();
            fonts.setAscii(FONT);
            fonts.setHAnsi(FONT);
            fonts.setCs(FONT);
            fonts.setEastAsia(FONT);
            styles.setDefaultFonts(fonts);
            style(styles, "Title", "Title", 48, true, "1F3864", 0, 120);
            style(styles, "Heading1", "heading 1", 32, true, "1F3864", 360, 120);
            style(styles, "Heading2", "heading 2", 26, true, "2F5496", 280, 100);
            style(styles, "Heading3", "heading 3", 24, true, "2F5496", 240, 80);
            style(styles, "Heading4", "heading 4", 22, true, "2F5496", 200, 60);
        }

        private static void style(XWPFStyles styles, String id, String name, int halfPoints, boolean bold, String color, int before, int after) {
            CTStyle ct = CTStyle.Factory.newInstance();
            ct.setStyleId(id);
            ct.setType(STStyleType.PARAGRAPH);
            ct.addNewName().setVal(name);
            ct.addNewQFormat();
            CTPPrGeneral ppr = ct.addNewPPr();
            ppr.addNewKeepNext();
            CTSpacing spacing = ppr.addNewSpacing();
            spacing.setBefore(BigInteger.valueOf(before));
            spacing.setAfter(BigInteger.valueOf(after));
            CTRPr rpr = ct.addNewRPr();
            CTFonts fonts = rpr.addNewRFonts();
            fonts.setAscii(FONT);
            fonts.setHAnsi(FONT);
            fonts.setCs(FONT);
            rpr.addNewSz().setVal(BigInteger.valueOf(halfPoints));
            rpr.addNewSzCs().setVal(BigInteger.valueOf(halfPoints));
            if (bold) {
                rpr.addNewB();
                rpr.addNewBCs();
            }
            if (color != null) rpr.addNewColor().setVal(color);
            styles.addStyle(new XWPFStyle(ct));
        }

        private XWPFNumbering numbering() {
            if (numbering == null) numbering = doc.createNumbering();
            return numbering;
        }

        /** One shared bullet list definition. */
        private BigInteger bullets() {
            if (bulletNum == null) bulletNum = numbering().addNum(abstractNum(true));
            return bulletNum;
        }

        /** A fresh numbered list that starts at one. */
        private BigInteger newDecimal() {
            if (decimalAbstract == null) decimalAbstract = abstractNum(false);
            BigInteger numId = numbering().addNum(decimalAbstract);
            for (int level = 0; level < 3; level++) {
                CTNumLvl override = numbering().getNum(numId).getCTNum().addNewLvlOverride();
                override.setIlvl(BigInteger.valueOf(level));
                override.addNewStartOverride().setVal(BigInteger.ONE);
            }
            return numId;
        }

        private BigInteger abstractNum(boolean bullet) {
            CTAbstractNum abs = CTAbstractNum.Factory.newInstance();
            abs.setAbstractNumId(BigInteger.valueOf(abstractCount++));
            for (int level = 0; level < 3; level++) {
                CTLvl lvl = abs.addNewLvl();
                lvl.setIlvl(BigInteger.valueOf(level));
                lvl.addNewStart().setVal(BigInteger.ONE);
                if (bullet) {
                    lvl.addNewNumFmt().setVal(STNumberFormat.BULLET);
                    lvl.addNewLvlText().setVal(level == 0 ? "•" : level == 1 ? "◦" : "▪");
                    CTFonts fonts = lvl.addNewRPr().addNewRFonts();
                    fonts.setAscii(FONT);
                    fonts.setHAnsi(FONT);
                } else {
                    lvl.addNewNumFmt().setVal(level == 0 ? STNumberFormat.DECIMAL : level == 1 ? STNumberFormat.LOWER_LETTER : STNumberFormat.LOWER_ROMAN);
                    lvl.addNewLvlText().setVal("%" + (level + 1) + ".");
                }
                lvl.addNewLvlJc().setVal(STJc.LEFT);
                CTInd ind = lvl.addNewPPr().addNewInd();
                ind.setLeft(BigInteger.valueOf((long) TWIPS_PER_LEVEL * (level + 1)));
                ind.setHanging(BigInteger.valueOf(360));
            }
            return numbering().addAbstractNum(new XWPFAbstractNum(abs));
        }

        // ----- blocks -----

        private static boolean isBlock(Element element) {
            return BLOCKS.contains(element.normalName());
        }

        private static boolean isList(Element element) {
            return "ul".equals(element.normalName()) || "ol".equals(element.normalName());
        }

        private static boolean hasBlockChildren(Element element) {
            for (Element child : element.children()) if (isBlock(child)) return true;
            return false;
        }

        private XWPFParagraph paragraph(Sink sink, Context context) {
            XWPFParagraph p = sink.paragraph();
            if (context.indent() > 0) p.setIndentationLeft(context.indent());
            if (context.quote()) p.setBorderLeft(Borders.SINGLE);
            p.setSpacingAfter(context.inCell() ? 40 : 120);
            return p;
        }

        /** Children of {@code parent} as blocks; runs of inline nodes between blocks become one paragraph. */
        private void blocks(Element parent, Sink sink, Context context) {
            XWPFParagraph open = null;
            for (Node node : List.copyOf(parent.childNodes())) {
                if (node instanceof TextNode textNode) {
                    if (textNode.isBlank()) continue;
                    if (open == null) open = paragraph(sink, context);
                    inline(textNode, open, format(context));
                } else if (node instanceof Element element) {
                    if (isBlock(element)) {
                        open = null;
                        block(element, sink, context);
                    } else {
                        if (open == null) open = paragraph(sink, context);
                        inline(element, open, format(context));
                    }
                }
            }
        }

        private static Format format(Context context) {
            return context.head() ? Format.PLAIN.withBold() : Format.PLAIN;
        }

        private void block(Element element, Sink sink, Context context) {
            switch (element.normalName()) {
                case "h1", "h2", "h3", "h4", "h5", "h6" -> {
                    XWPFParagraph p = paragraph(sink, context);
                    p.setStyle("Heading" + Math.min(element.normalName().charAt(1) - '0', 4));
                    align(p, element);
                    inlines(element, p, format(context).color(color(element)));
                }
                case "ul", "ol" -> list(element, sink, context, 0);
                case "blockquote" -> blocks(element, sink, context.quoted());
                case "pre" -> pre(element, sink, context);
                case "table" -> {
                    if (context.inCell()) blocks(element, sink, context);
                    else table(element, context);
                }
                case "hr" -> paragraph(sink, context).setBorderBottom(Borders.SINGLE);
                case "li", "thead", "tbody", "tfoot", "tr", "td", "th" -> blocks(element, sink, context);
                default -> {
                    if (hasBlockChildren(element)) {
                        blocks(element, sink, context);
                    } else {
                        XWPFParagraph p = paragraph(sink, context);
                        align(p, element);
                        Format format = format(context).color(color(element));
                        if ("figcaption".equals(element.normalName())) format = format.withItalic();
                        inlines(element, p, format);
                    }
                }
            }
        }

        private void list(Element listElement, Sink sink, Context context, int level) {
            BigInteger numId = "ol".equals(listElement.normalName()) ? newDecimal() : bullets();
            for (Element item : listElement.children()) {
                if (!"li".equals(item.normalName())) continue;
                XWPFParagraph numbered = paragraph(sink, context);
                numbered.setNumID(numId);
                numbered.setNumILvl(BigInteger.valueOf(Math.min(level, 2)));
                XWPFParagraph current = numbered;
                boolean numberedUsed = false;
                for (Node child : List.copyOf(item.childNodes())) {
                    if (child instanceof TextNode textNode && textNode.isBlank()) continue;
                    if (child instanceof Element nested && isList(nested)) {
                        list(nested, sink, context, level + 1);
                        current = null;
                    } else if (child instanceof Element nestedBlock && isBlock(nestedBlock)) {
                        String tag = nestedBlock.normalName();
                        if ("pre".equals(tag) || "table".equals(tag) || "blockquote".equals(tag) || hasBlockChildren(nestedBlock)) {
                            block(nestedBlock, sink, context.indented(level + 1));
                            current = null;
                            continue;
                        }
                        if (current != numbered || numberedUsed) current = continuation(sink, context, level);
                        align(current, nestedBlock);
                        inlines(nestedBlock, current, format(context).color(color(nestedBlock)));
                        if (current == numbered) numberedUsed = true;
                        current = null;
                    } else {
                        if (current == null) current = continuation(sink, context, level);
                        inline(child, current, format(context));
                        if (current == numbered) numberedUsed = true;
                    }
                }
            }
        }

        /** A paragraph below a list item, aligned with its text. */
        private XWPFParagraph continuation(Sink sink, Context context, int level) {
            XWPFParagraph p = paragraph(sink, context);
            p.setIndentationLeft(context.indent() + TWIPS_PER_LEVEL * (level + 1));
            return p;
        }

        private void pre(Element element, Sink sink, Context context) {
            XWPFParagraph p = paragraph(sink, context);
            shade(p, "F4F4F4");
            String code = element.wholeText().replace("\r\n", "\n").replace('\r', '\n');
            if (code.endsWith("\n")) code = code.substring(0, code.length() - 1);
            String[] lines = code.split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                XWPFRun run = p.createRun();
                if (i > 0) run.addBreak();
                mono(run);
                run.setText(lines[i]);
            }
        }

        private void table(Element element, Context context) {
            List<Element> rows = new ArrayList<>();
            for (Element child : element.children()) {
                if ("tr".equals(child.normalName())) rows.add(child);
                else if ("thead".equals(child.normalName()) || "tbody".equals(child.normalName()) || "tfoot".equals(child.normalName())) {
                    for (Element row : child.children()) if ("tr".equals(row.normalName())) rows.add(row);
                }
            }
            if (rows.isEmpty()) return;
            XWPFTable table = doc.createTable();
            table.setWidthType(TableWidthType.PCT);
            table.setWidth("100%");
            table.setCellMargins(40, 80, 40, 80);
            int rowIndex = 0;
            for (Element tr : rows) {
                XWPFTableRow row = rowIndex == 0 ? table.getRow(0) : table.createRow();
                int cellIndex = 0;
                for (Element td : tr.children()) {
                    boolean head = "th".equals(td.normalName());
                    if (!head && !"td".equals(td.normalName())) continue;
                    XWPFTableCell cell = cellIndex < row.getTableCells().size() ? row.getCell(cellIndex) : row.createCell();
                    if (head) cell.setColor("EEEEEE");
                    blocks(td, cellSink(cell), context.cell(head));
                    cellIndex++;
                }
                rowIndex++;
            }
            // Word needs a paragraph after a table before the next block.
            doc.createParagraph().setSpacingAfter(0);
        }

        private static Sink cellSink(XWPFTableCell cell) {
            return new Sink() {
                private boolean used;

                @Override
                public XWPFParagraph paragraph() {
                    if (!used && !cell.getParagraphs().isEmpty()) {
                        used = true;
                        return cell.getParagraphs().getFirst();
                    }
                    return cell.addParagraph();
                }
            };
        }

        // ----- inline content -----

        private void inlines(Element element, XWPFParagraph p, Format format) {
            for (Node child : element.childNodes()) inline(child, p, format);
        }

        private void inline(Node node, XWPFParagraph p, Format format) {
            if (node instanceof TextNode textNode) {
                String value = collapse(textNode.getWholeText());
                if (!value.isEmpty()) text(p, value, format);
                return;
            }
            if (!(node instanceof Element element)) return;
            switch (element.normalName()) {
                case "strong", "b" -> inlines(element, p, format.withBold().color(color(element)));
                case "em", "i", "cite", "var" -> inlines(element, p, format.withItalic().color(color(element)));
                case "u", "ins" -> inlines(element, p, format.withUnderline().color(color(element)));
                case "s", "strike", "del" -> inlines(element, p, format.withStrike().color(color(element)));
                case "code", "kbd", "samp", "tt" -> inlines(element, p, format.withCode());
                case "mark" -> inlines(element, p, format.withHighlight());
                case "sup" -> inlines(element, p, format.vertical("superscript"));
                case "sub" -> inlines(element, p, format.vertical("subscript"));
                case "a" -> {
                    String href = element.attr("href").trim();
                    inlines(element, p, linkable(href) ? format.link(href) : format.color(color(element)));
                }
                case "br" -> p.createRun().addBreak();
                case "img" -> picture(element, p);
                default -> {
                    if (isBlock(element) && p.getRuns() != null && !p.getRuns().isEmpty()) p.createRun().addBreak();
                    inlines(element, p, format.color(color(element)));
                }
            }
        }

        private void text(XWPFParagraph p, String value, Format format) {
            XWPFRun run = format.link() != null ? p.createHyperlinkRun(format.link()) : p.createRun();
            // Paragraph styles (title, headings) carry their own size; a run size would override it.
            run.setFontFamily(FONT);
            if (p.getStyle() == null) run.setFontSize(11);
            run.setText(value);
            if (format.bold()) run.setBold(true);
            if (format.italic()) run.setItalic(true);
            if (format.underline() || format.link() != null) run.setUnderline(UnderlinePatterns.SINGLE);
            if (format.strike()) run.setStrikeThrough(true);
            if (format.code()) {
                mono(run);
                run.setTextHighlightColor("lightGray");
            }
            if (format.highlight()) run.setTextHighlightColor("yellow");
            if (format.link() != null) run.setColor("0563C1");
            else if (format.color() != null) run.setColor(format.color());
            if (format.vertical() != null) run.setVerticalAlignment(format.vertical());
        }

        private void picture(Element img, XWPFParagraph p) {
            ExportImage image = document.image(img);
            if (image == null) {
                text(p, text.imageOmitted(ExportDocument.describe(img)), Format.PLAIN.withItalic().color("777777"));
                return;
            }
            int[] size = image.fit(MAX_IMAGE_WIDTH);
            XWPFRun run = p.createRun();
            try {
                run.addPicture(new ByteArrayInputStream(image.bytes()), pictureType(image.mediaType()), image.name(),
                        Units.pixelToEMU(size[0]), Units.pixelToEMU(size[1]));
            } catch (InvalidFormatException | IOException ex) {
                text(p, text.imageOmitted(image.name()), Format.PLAIN.withItalic().color("777777"));
            }
        }

        private static PictureType pictureType(String mediaType) {
            return switch (mediaType) {
                case "image/jpeg" -> PictureType.JPEG;
                case "image/gif" -> PictureType.GIF;
                case "image/bmp" -> PictureType.BMP;
                default -> PictureType.PNG;
            };
        }

        // ----- helpers -----

        private static void base(XWPFRun run) {
            run.setFontFamily(FONT);
            run.setFontSize(11);
        }

        private static void mono(XWPFRun run) {
            run.setFontFamily(MONO);
            run.setFontSize(10);
        }

        private static void shade(XWPFParagraph p, String fill) {
            CTPPr ppr = p.getCTP().isSetPPr() ? p.getCTP().getPPr() : p.getCTP().addNewPPr();
            CTShd shd = ppr.isSetShd() ? ppr.getShd() : ppr.addNewShd();
            shd.setVal(STShd.CLEAR);
            shd.setColor("auto");
            shd.setFill(fill);
        }

        private static void align(XWPFParagraph p, Element element) {
            String style = element.attr("style").toLowerCase(Locale.ROOT);
            if (style.contains("text-align:center") || style.contains("text-align: center")) p.setAlignment(ParagraphAlignment.CENTER);
            else if (style.contains("text-align:right") || style.contains("text-align: right")) p.setAlignment(ParagraphAlignment.RIGHT);
            else if (style.contains("text-align:justify") || style.contains("text-align: justify")) p.setAlignment(ParagraphAlignment.BOTH);
        }

        /** The {@code color} of an inline style as six hex digits, or null. */
        static String color(Element element) {
            String style = element.attr("style");
            if (style.isEmpty()) return null;
            for (String declaration : style.split(";")) {
                String[] parts = declaration.split(":", 2);
                if (parts.length != 2 || !parts[0].trim().equalsIgnoreCase("color")) continue;
                String value = parts[1].trim().toLowerCase(Locale.ROOT);
                Matcher hex = HEX.matcher(value);
                if (hex.find()) {
                    String digits = hex.group(1);
                    if (digits.length() == 3) digits = "" + digits.charAt(0) + digits.charAt(0) + digits.charAt(1) + digits.charAt(1) + digits.charAt(2) + digits.charAt(2);
                    return digits.toUpperCase(Locale.ROOT);
                }
                Matcher rgb = RGB.matcher(value);
                if (rgb.find()) {
                    return String.format("%02X%02X%02X", clamp(rgb.group(1)), clamp(rgb.group(2)), clamp(rgb.group(3)));
                }
                return NAMED_COLORS.get(value);
            }
            return null;
        }

        private static int clamp(String value) {
            return Math.min(255, Integer.parseInt(value));
        }

        private static boolean linkable(String href) {
            String lower = href.toLowerCase(Locale.ROOT);
            return lower.startsWith("https://") || lower.startsWith("http://") || lower.startsWith("mailto:");
        }

        private static String collapse(String value) {
            return value.replaceAll("[\\s\\u00a0]+", " ");
        }
    }
}
