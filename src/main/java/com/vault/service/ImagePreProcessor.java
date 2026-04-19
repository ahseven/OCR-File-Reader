package com.vault.service;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ImagePreProcessor {

    public File preprocessForOcr(File inputImage) throws IOException {
        long startTime = System.currentTimeMillis();
        BufferedImage img = ImageIO.read(inputImage);
        if (img == null) {
            throw new IOException("Could not read image for pre-processing: " + inputImage.getName());
        }

        log.info("Starting pre-processing for {}...", inputImage.getName());
        
        // 1. Ensure RGB type for compatibility
        BufferedImage rgbImage = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
        rgbImage.getGraphics().drawImage(img, 0, 0, null);
        
        // 2. Convert to Grayscale
        BufferedImage gray = convertToGrayscale(rgbImage);
        
        // 3. Enhance contrast
        BufferedImage enhanced = enhanceContrast(gray);

        File cleanedFile = new File(inputImage.getParent(), "cleaned_" + System.nanoTime() + ".png");
        if (!ImageIO.write(enhanced, "png", cleanedFile)) {
            throw new IOException("Failed to write cleaned image to disk");
        }
        
        log.info("Pre-processing completed in {}ms. Saved to: {}", (System.currentTimeMillis() - startTime), cleanedFile.getName());
        
        return cleanedFile;
    }

    private BufferedImage convertToGrayscale(BufferedImage src) {
        BufferedImage gray = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int rgb = src.getRGB(x, y);
                int r = (rgb >> 16) & 0xff;
                int g = (rgb >> 8) & 0xff;
                int b = rgb & 0xff;
                int grayValue = (int)(0.299 * r + 0.587 * g + 0.114 * b);
                int grayRgb = (grayValue << 16) | (grayValue << 8) | grayValue;
                gray.setRGB(x, y, grayRgb);
            }
        }
        return gray;
    }

    private BufferedImage enhanceContrast(BufferedImage src) {
        BufferedImage enhanced = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        
        // Find min and max pixel values
        int minVal = 255, maxVal = 0;
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int gray = src.getRGB(x, y) & 0xff;
                minVal = Math.min(minVal, gray);
                maxVal = Math.max(maxVal, gray);
            }
        }
        
        // Stretch contrast
        int range = maxVal - minVal;
        if (range == 0) range = 1;
        
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int gray = src.getRGB(x, y) & 0xff;
                int stretched = (int)(((gray - minVal) * 255.0) / range);
                stretched = Math.max(0, Math.min(255, stretched));
                int rgb = (stretched << 16) | (stretched << 8) | stretched;
                enhanced.setRGB(x, y, rgb);
            }
        }
        
        return enhanced;
    }
}
