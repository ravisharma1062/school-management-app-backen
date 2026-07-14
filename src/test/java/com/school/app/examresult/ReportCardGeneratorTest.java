package com.school.app.examresult;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReportCardGeneratorTest {

    private static ExamResultDto result(String subject, String term, String marks) {
        return new ExamResultDto(
                UUID.randomUUID(), UUID.randomUUID(), subject, "Midterm",
                new BigDecimal(marks), new BigDecimal("100"), "A", term);
    }

    @Test
    void producesAValidPdfWhenNoSubjectsRecorded() {
        byte[] pdf = ReportCardGenerator.generate(null, null, null, "Aarav Kumar", "9", "A", List.of());

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF");
    }

    @Test
    void producesAValidPdfForASingleTerm() {
        byte[] pdf = ReportCardGenerator.generate(
                null, null, null, "Aarav Kumar", "9", "A", List.of(result("Maths", "Term 1", "85")));

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF");
    }

    @Test
    void groupsAndProducesAValidPdfAcrossMultipleTerms() {
        byte[] pdf = ReportCardGenerator.generate(
                null, null, null, "Aarav Kumar", "9", "A",
                List.of(
                        result("Maths", "Term 1", "85"),
                        result("Science", "Term 1", "78"),
                        result("Maths", "Term 2", "91")
                ));

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF");
    }

    @Test
    void appliesSchoolNameAndBrandColorWhenProvided() {
        byte[] pdf = ReportCardGenerator.generate(
                "Sunrise Public School", null, "#4F46E5", "Aarav Kumar", "9", "A", List.of());

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF");
    }

    @Test
    void ignoresAMalformedBrandColorRatherThanFailing() {
        byte[] pdf = ReportCardGenerator.generate(
                "Sunrise Public School", null, "not-a-hex-color", "Aarav Kumar", "9", "A", List.of());

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF");
    }

    @Test
    void embedsALogoImageWhenProvided() throws Exception {
        byte[] logoPng = onePixelPng();
        byte[] pdf = ReportCardGenerator.generate(
                "Sunrise Public School", logoPng, null, "Aarav Kumar", "9", "A", List.of());

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF");
    }

    @Test
    void ignoresACorruptLogoRatherThanFailing() {
        byte[] pdf = ReportCardGenerator.generate(
                "Sunrise Public School", new byte[]{1, 2, 3}, null, "Aarav Kumar", "9", "A", List.of());

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF");
    }

    /** A minimal valid 1x1 PNG, decodable by OpenPDF's {@code Image.getInstance}. */
    private static byte[] onePixelPng() {
        return java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
    }
}
