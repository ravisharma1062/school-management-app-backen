package com.school.app.examresult;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Pure PDF-rendering logic for a student's report card — no Spring/DB dependencies, unit-testable directly. */
public final class ReportCardGenerator {

    private ReportCardGenerator() {
    }

    /**
     * @param schoolName    always shown; falls back to a generic title if blank.
     * @param logoBytes     the school's logo image, or {@code null} if it has none set / isn't
     *                      entitled to branding — the caller (ExamResultService) is what decides that.
     * @param primaryColorHex a {@code #RRGGBB} hex string, or {@code null} for the default black.
     */
    public static byte[] generate(
            String schoolName, byte[] logoBytes, String primaryColorHex,
            String studentName, String studentClass, String section, List<ExamResultDto> results) {
        Document document = new Document();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(document, out);
            document.open();

            Color brandColor = parseHexColor(primaryColorHex);
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, brandColor != null ? brandColor : Color.BLACK);
            Font headingFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, brandColor != null ? brandColor : Color.BLACK);
            Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 11);
            Font headerCellFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);

            if (logoBytes != null) {
                try {
                    Image logo = Image.getInstance(logoBytes);
                    logo.scaleToFit(90, 90);
                    document.add(logo);
                } catch (Exception e) {
                    // A corrupt/unsupported logo image must never block report-card generation.
                }
            }

            document.add(new Paragraph(schoolName != null && !schoolName.isBlank() ? schoolName : "Report Card", titleFont));
            document.add(new Paragraph("Report Card", headingFont));
            document.add(new Paragraph(studentName + " — Class " + studentClass + "-" + section, normalFont));
            document.add(Chunk.NEWLINE);

            if (results.isEmpty()) {
                document.add(new Paragraph("No exam results have been recorded yet.", normalFont));
            } else {
                // TreeMap: terms print in a stable, alphabetically sorted order regardless of DB order.
                Map<String, List<ExamResultDto>> byTerm = new TreeMap<>();
                for (ExamResultDto result : results) {
                    byTerm.computeIfAbsent(result.term(), t -> new ArrayList<>()).add(result);
                }

                for (Map.Entry<String, List<ExamResultDto>> entry : byTerm.entrySet()) {
                    document.add(new Paragraph(entry.getKey(), headingFont));

                    PdfPTable table = new PdfPTable(4);
                    table.setWidthPercentage(100);
                    table.addCell(headerCell("Subject", headerCellFont));
                    table.addCell(headerCell("Exam", headerCellFont));
                    table.addCell(headerCell("Marks", headerCellFont));
                    table.addCell(headerCell("Grade", headerCellFont));

                    for (ExamResultDto result : entry.getValue()) {
                        table.addCell(new Phrase(result.subject(), normalFont));
                        table.addCell(new Phrase(result.examName(), normalFont));
                        table.addCell(new Phrase(result.marksObtained() + " / " + result.maxMarks(), normalFont));
                        table.addCell(new Phrase(result.grade(), normalFont));
                    }

                    document.add(table);
                    document.add(Chunk.NEWLINE);
                }
            }

            document.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to generate report card PDF", e);
        }
        return out.toByteArray();
    }

    private static PdfPCell headerCell(String text, Font font) {
        return new PdfPCell(new Phrase(text, font));
    }

    private static Color parseHexColor(String hex) {
        if (hex == null || !hex.matches("^#[0-9A-Fa-f]{6}$")) {
            return null;
        }
        return Color.decode(hex);
    }
}
