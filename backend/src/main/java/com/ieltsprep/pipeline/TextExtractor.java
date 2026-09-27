package com.ieltsprep.pipeline;

import java.io.IOException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/** Plain text from fetched HTML (main content paragraphs) or PDF. */
final class TextExtractor {

    private TextExtractor() {}

    static String text(PoliteFetcher.Fetched f) throws IOException {
        if (f.isPdf()) {
            try (PDDocument doc = Loader.loadPDF(f.body())) {
                return new PDFTextStripper().getText(doc).replaceAll("[ \\t]+", " ").trim();
            }
        }
        Document doc = Jsoup.parse(f.text(), f.url());
        doc.select("script, style, nav, header, footer, noscript, .reference, .mw-editsection, sup, table.infobox").remove();
        Element main = doc.selectFirst("#mw-content-text");
        if (main == null) {
            main = doc.selectFirst("main");
        }
        if (main == null) {
            main = doc.body();
        }
        StringBuilder sb = new StringBuilder();
        for (Element el : main.select("h1, h2, h3, p, li")) {
            String t = el.text().trim();
            if (t.length() > 1) {
                sb.append(el.tagName().startsWith("h") ? "\n## " : "").append(t).append("\n");
            }
        }
        return sb.toString().trim();
    }
}
