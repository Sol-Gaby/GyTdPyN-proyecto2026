package proyecto.cursos;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

/** Generación versionable de propuestas e informes a partir de su texto. */
final class DocumentosCurso {
    private DocumentosCurso() { }

    static byte[] pdf(String texto) throws IOException {
        List<String> lineas = new ArrayList<>();
        for (String linea : texto.replaceAll("[^\\x20-\\x7E\\xA0-\\xFF\\n]", "?").split("\n", -1)) {
            if (linea.isEmpty()) lineas.add("");
            for (int inicio = 0; inicio < linea.length(); inicio += 82) {
                lineas.add(linea.substring(inicio, Math.min(inicio + 82, linea.length())));
            }
        }
        try (PDDocument documento = new PDDocument(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            for (int inicio = 0; inicio < lineas.size(); inicio += 45) {
                PDPage pagina = new PDPage();
                documento.addPage(pagina);
                try (PDPageContentStream contenido = new PDPageContentStream(documento, pagina)) {
                    contenido.beginText();
                    contenido.setFont(PDType1Font.HELVETICA, 10);
                    contenido.newLineAtOffset(42, 748);
                    contenido.setLeading(14);
                    for (int linea = inicio; linea < Math.min(inicio + 45, lineas.size()); linea++) {
                        contenido.showText(lineas.get(linea));
                        contenido.newLine();
                    }
                    contenido.endText();
                }
            }
            documento.save(salida);
            return salida.toByteArray();
        }
    }
}
