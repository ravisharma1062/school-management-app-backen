package com.school.app.examresult;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Pure PDF-rendering logic for a student's report card — no Spring/DB dependencies, unit-testable directly. */
public final class ReportCardGenerator {

    private ReportCardGenerator() {
    }

    public static byte[] generate(String studentName, String studentClass, String section, List<ExamResultDto> results) {
        Document document = new Document();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            Font headingFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13);
            Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 11);
            Font headerCellFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);

            document.add(new Paragraph("Report Card", titleFont));
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
}
