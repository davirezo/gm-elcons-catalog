package br.com.gmelcons.catalog.service;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Identifica o formato da imagem pelo CONTEÚDO (bytes iniciais), ignorando nome e Content-Type enviados
 * pelo cliente, que podem ser forjados. Só JPEG, PNG e WebP são aceitos (SVG fica de fora de propósito:
 * pode conter script).
 */
public final class ImageSniffer {

    public enum Kind {
        JPEG("image/jpeg", "jpg"), PNG("image/png", "png"), WEBP("image/webp", "webp");

        public final String contentType;
        public final String extension;

        Kind(String contentType, String extension) {
            this.contentType = contentType;
            this.extension = extension;
        }
    }

    private ImageSniffer() {
    }

    /** Formato detectado, ou null se não for JPEG, PNG ou WebP. */
    public static Kind detect(byte[] b) {
        if (b == null) {
            return null;
        }
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return Kind.JPEG;
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
            return Kind.PNG;
        }
        if (b.length >= 16 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return Kind.WEBP;
        }
        return null;
    }

    /**
     * Largura e altura lidas do cabeçalho (sem decodificar a imagem inteira), para JPEG e PNG.
     * Devolve null se o arquivo não puder ser lido como imagem.
     */
    public static int[] dimensions(byte[] data) {
        try (ImageInputStream in = new MemoryCacheImageInputStream(new ByteArrayInputStream(data))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in);
                return new int[]{reader.getWidth(0), reader.getHeight(0)};
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
