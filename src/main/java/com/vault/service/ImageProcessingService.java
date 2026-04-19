package com.vault.service;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.awt.image.RescaleOp;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ImageProcessingService {

    @Value("${app.upload.dir}")
    private String uploadDir;

    public String processAndSaveImage(MultipartFile file, UUID noteId) throws IOException {
        long originalSize = file.getSize();
        String contentType = file.getContentType();
        log.info("Processing upload: {} (Type: {})", file.getOriginalFilename(), contentType);

        ensureUploadDirectoryExists();

        // Use try-with-resources to ensure the input stream is closed automatically
        try (InputStream inputStream = file.getInputStream()) {
            BufferedImage image;
            
            if ("application/pdf".equalsIgnoreCase(contentType)) {
                image = renderFirstPageOfPdf(inputStream);
            } else {
                image = ImageIO.read(inputStream);
            }
            
            if (image == null) throw new IOException("Invalid image format");

            image = optimizeImage(image);
            
            String filename = noteId.toString() + ".png";
            saveImageToDisk(image, filename);
            
            logEfficiency(originalSize, filename);
            return filename;
        }
    }

    private BufferedImage renderFirstPageOfPdf(InputStream inputStream) throws IOException {
        try (PDDocument document = Loader.loadPDF(inputStream.readAllBytes())) {
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            return pdfRenderer.renderImageWithDPI(0, 300); // First page only for UI thumbnail
        }
    }

    private void ensureUploadDirectoryExists() throws IOException {
        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }
    }

    private BufferedImage optimizeImage(BufferedImage original) {
        BufferedImage resized = resizeToStandardWidth(original, 1200);
        BufferedImage gray = convertToGrayscale(resized);
        return applyContrast(gray, 1.2f, 15);
    }

    private BufferedImage resizeToStandardWidth(BufferedImage src, int targetWidth) {
        int width = src.getWidth();
        int height = src.getHeight();
        if (width <= targetWidth) return src;

        double ratio = (double) targetWidth / width;
        int newHeight = (int) (height * ratio);

        BufferedImage resized = new BufferedImage(targetWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = resized.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.drawImage(src, 0, 0, targetWidth, newHeight, null);
        g2d.dispose();
        return resized;
    }

    private BufferedImage convertToGrayscale(BufferedImage src) {
        ColorConvertOp op = new ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_GRAY), null);
        return op.filter(src, null);
    }

    private BufferedImage applyContrast(BufferedImage src, float scale, float offset) {
        RescaleOp rescaleOp = new RescaleOp(scale, offset, null);
        return rescaleOp.filter(src, null);
    }

    private void saveImageToDisk(BufferedImage image, String filename) throws IOException {
        File outputFile = Paths.get(uploadDir, filename).toFile();
        if (!ImageIO.write(image, "png", outputFile)) {
            throw new IOException("Failed to write PNG to disk");
        }
    }

    private void logEfficiency(long originalSize, String filename) {
        File file = Paths.get(uploadDir, filename).toFile();
        long newSize = file.length();
        double reduction = (1.0 - ((double) newSize / originalSize)) * 100;
        log.info("Optimization complete: {} bytes saved ({}% reduction)", (originalSize - newSize), String.format("%.2f", reduction));
    }
}
