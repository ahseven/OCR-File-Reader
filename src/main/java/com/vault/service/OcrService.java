package com.vault.service;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.vault.domain.Note;
import com.vault.domain.NoteFileStore;
import com.vault.domain.NoteRepository;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.sourceforge.tess4j.Tesseract;

@Service
public class OcrService {

    private static final Logger log = LoggerFactory.getLogger(OcrService.class);

    @Value("${app.tessdata.dir}")
    private String tessdataDir;

    @Value("${app.tessdata.url}")
    private String tessdataUrl;

    @Value("${app.upload.dir}")
    private String uploadDir;

    private final NoteRepository noteRepository;
    private final NoteFileStore noteFileStore;
    private final ImagePreProcessor imagePreProcessor;
    private final ThreadPoolExecutor ocrThreadPool;
    private final ThreadPoolExecutor dbWriteExecutor;
    private final AtomicInteger ocrSubmittedTasks = new AtomicInteger(0);
    private final AtomicInteger ocrCompletedTasks = new AtomicInteger(0);
    private final AtomicInteger ocrFailedTasks = new AtomicInteger(0);

    public OcrService(NoteRepository noteRepository, NoteFileStore noteFileStore, ImagePreProcessor imagePreProcessor) {
        this.noteRepository = noteRepository;
        this.noteFileStore = noteFileStore;
        this.imagePreProcessor = imagePreProcessor;
        // Use all available CPU cores for OCR workers; database writes remain serialized.
        int cores = Math.max(1, Runtime.getRuntime().availableProcessors());
        this.ocrThreadPool = new ThreadPoolExecutor(
                cores,
                cores,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(64),
                new ThreadPoolExecutor.CallerRunsPolicy());
        // Single-threaded DB writer to avoid SQLite write contention.
        this.dbWriteExecutor = new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(1024),
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    @PostConstruct
    public void init() {
        Path tessdataPath = Paths.get(tessdataDir);
        File trainedData = new File(tessdataPath.toFile(), "eng.traineddata");

        try {
            if (!Files.exists(tessdataPath)) {
                Files.createDirectories(tessdataPath);
            }

            if (!trainedData.exists()) {
                log.info("eng.traineddata not found locally. Downloading from standard Tesseract repository...");
                downloadTessdata(trainedData);
                log.info("Download complete!");
            } else {
                log.info("eng.traineddata found locally. Tesseract is ready.");
            }
        } catch (IOException e) {
            log.error("Failed to initialize tessdata: ", e);
        }
    }

    private void downloadTessdata(File targetFile) throws IOException {
        URL website = java.net.URI.create(tessdataUrl).toURL();
        try (ReadableByteChannel rbc = Channels.newChannel(website.openStream());
             FileOutputStream fos = new FileOutputStream(targetFile)) {
            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
        }
    }

    public void processOcrAsync(Note note, String filename, byte[] fileBytes, boolean isPdf) {
        ocrSubmittedTasks.incrementAndGet();
        ocrThreadPool.submit(() -> performOcrTask(note, filename, fileBytes, isPdf));
    }

    private void performOcrTask(Note note, String filename, byte[] fileBytes, boolean isPdf) {
        try {
            log.info("Starting OCR processing for Note UUID: {}", note.getId());
            StringBuilder fullText = new StringBuilder();

            if (isPdf) {
                processMultiPagePdf(fileBytes, fullText);
            } else {
                processSingleImage(filename, fullText);
            }
            
            String text = fullText.toString();
            log.info("OCR completed for Note UUID: {}. Text length: {}", note.getId(), text.length());
            ocrCompletedTasks.incrementAndGet();
            enqueueNoteUpdate(note.getId(), text, "COMPLETED");
        } catch (Exception e) {
            log.error("OCR Failed for Note UUID: {}", note.getId(), e);
            ocrFailedTasks.incrementAndGet();
            enqueueNoteUpdate(note.getId(), "ERROR: " + e.getMessage(), "FAILED");
        }
    }

    private void processMultiPagePdf(byte[] fileBytes, StringBuilder output) throws Exception {
        try (PDDocument document = Loader.loadPDF(fileBytes)) {
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            int pageCount = document.getNumberOfPages();
            log.info("Scanning {} pages from PDF for OCR...", pageCount);

            for (int i = 0; i < pageCount; i++) {
                BufferedImage pageImage = pdfRenderer.renderImageWithDPI(i, 300);
                File tempPageFile = File.createTempFile("ocr_page_" + i, ".png");
                
                try {
                    ImageIO.write(pageImage, "png", tempPageFile);
                    File cleanedImage = imagePreProcessor.preprocessForOcr(tempPageFile);
                    
                    try {
                        output.append(executeTesseract(cleanedImage)).append("\n\n");
                    } finally {
                        cleanupTempFiles(cleanedImage);
                    }
                } finally {
                    cleanupTempFiles(tempPageFile);
                }
            }
        }
    }

    private void processSingleImage(String filename, StringBuilder output) throws Exception {
        File imageFile = Paths.get(uploadDir, filename).toFile();
        File cleanedImage = null;
        
        try {
            cleanedImage = imagePreProcessor.preprocessForOcr(imageFile);
            output.append(executeTesseract(cleanedImage));
        } finally {
            cleanupTempFiles(cleanedImage);
        }
    }

    private String executeTesseract(File image) throws Exception {
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath(tessdataDir);
        tesseract.setLanguage("eng");
        tesseract.setPageSegMode(3); 
        tesseract.setOcrEngineMode(1); 
        return tesseract.doOCR(image);
    }

    private void enqueueNoteUpdate(String noteId, String content, String status) {
        dbWriteExecutor.execute(() -> saveNoteUpdate(noteId, content, status));
    }

    public ProcessingStatus getProcessingStatus() {
        return new ProcessingStatus(
                ocrThreadPool.getCorePoolSize(),
                ocrThreadPool.getActiveCount(),
                ocrThreadPool.getQueue().size(),
                dbWriteExecutor.getQueue().size(),
                ocrSubmittedTasks.get(),
                ocrCompletedTasks.get(),
                ocrFailedTasks.get());
    }

    public record ProcessingStatus(
            int configuredOcrPoolSize,
            int activeOcrThreads,
            int ocrQueueSize,
            int dbQueueSize,
            int totalOcrSubmitted,
            int totalOcrCompleted,
            int totalOcrFailed) {
    }

    private void saveNoteUpdate(String noteId, String content, String status) {
        int maxAttempts = 5;
        long delayMs = 500;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                Note note = noteRepository.findById(noteId).orElse(null);
                if (note == null) {
                    log.error("Note {} not found when saving OCR result", noteId);
                    return;
                }
                note.setContent(content);
                note.setStatus(status);
                noteRepository.save(note);
                noteFileStore.saveNoteToFile(note);
                return;
            } catch (Exception e) {
                if (attempt >= maxAttempts || !isSqliteBusyError(e)) {
                    log.error("Failed to save note {} after {} attempts", noteId, attempt, e);
                    return;
                }
                log.warn("SQLite busy on DB writer attempt {}/{} for note {}. Retrying in {}ms...", attempt, maxAttempts, noteId, delayMs);
                if (!sleepMillis(delayMs)) {
                    return;
                }
                delayMs = Math.min(2000, delayMs * 2);
            }
        }
    }

    private boolean isSqliteBusyError(Throwable throwable) {
        while (throwable != null) {
            String message = throwable.getMessage();
            if (message != null && (message.contains("database is locked") || message.contains("SQLITE_BUSY"))) {
                return true;
            }
            throwable = throwable.getCause();
        }
        return false;
    }

    private boolean sleepMillis(long millis) {
        try {
            TimeUnit.MILLISECONDS.sleep(millis);
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void cleanupTempFiles(File file) {
        if (file != null && file.exists()) {
            if (!file.delete()) {
                log.warn("Failed to delete temporary cleaned image: {}", file.getAbsolutePath());
            }
        }
    }
}
