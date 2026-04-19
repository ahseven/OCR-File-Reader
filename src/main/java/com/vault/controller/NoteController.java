package com.vault.controller;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.vault.domain.Note;
import com.vault.domain.NoteFileStore;
import com.vault.domain.NoteRepository;
import com.vault.service.ImageProcessingService;
import com.vault.service.OcrService;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/notes")
@RequiredArgsConstructor
public class NoteController {

    private final ImageProcessingService imageProcessingService;
    private final OcrService ocrService;
    private final NoteRepository noteRepository;
    private final NoteFileStore noteFileStore;

    @PostMapping("/upload")
    public ResponseEntity<?> uploadNote(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("File is empty.");
        }

        Note note = null;
        try {
            note = createPersistedNote(file.getOriginalFilename());
            String savedFilename = imageProcessingService.processAndSaveImage(file, UUID.fromString(note.getId()));
            note.setFilename("/processed-notes/" + savedFilename);
            note = noteRepository.save(note);
            noteFileStore.saveNoteToFile(note);

            boolean isPdf = isPdfFile(file.getOriginalFilename());
            byte[] fileBytes = file.getBytes();
            ocrService.processOcrAsync(note, savedFilename, fileBytes, isPdf);

            return ResponseEntity.ok(note);
        } catch (IOException e) {
            if (note != null) {
                note.setStatus("FAILED");
                note.setContent("IMAGE PROCESSING FAILED: " + e.getMessage());
                noteRepository.save(note);
                noteFileStore.saveNoteToFile(note);
            }
            return ResponseEntity.internalServerError().body("Failed to process file: " + e.getMessage());
        }
    }

    @PostMapping("/upload-multiple")
    public ResponseEntity<?> uploadNotes(@RequestParam("files") MultipartFile[] files) {
        if (files == null || files.length == 0) {
            return ResponseEntity.badRequest().body("No files provided.");
        }

        List<UploadResult> results = Arrays.stream(files)
                .map(file -> processSingleUpload(file))
                .collect(Collectors.toList());

        return ResponseEntity.ok(new BulkUploadResponse(results));
    }

    private UploadResult processSingleUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return new UploadResult(null, null, "SKIPPED: empty file");
        }

        Note note = createPersistedNote(file.getOriginalFilename());
        try {
            String savedFilename = imageProcessingService.processAndSaveImage(file, UUID.fromString(note.getId()));
            note.setFilename("/processed-notes/" + savedFilename);
            note = noteRepository.save(note);
            noteFileStore.saveNoteToFile(note);

            boolean isPdf = isPdfFile(file.getOriginalFilename());
            byte[] fileBytes = file.getBytes();
            ocrService.processOcrAsync(note, savedFilename, fileBytes, isPdf);

            return new UploadResult(note.getId(), file.getOriginalFilename(), "PROCESSING");
        } catch (IOException e) {
            note.setStatus("FAILED");
            note.setContent("IMAGE PROCESSING FAILED: " + e.getMessage());
            noteRepository.save(note);
            noteFileStore.saveNoteToFile(note);
            return new UploadResult(note.getId(), file.getOriginalFilename(), "FAILED: " + e.getMessage());
        }
    }

    private Note createPersistedNote(String originalFilename) {
        Note note = new Note();
        note.setOriginalFilename(originalFilename);
        return noteRepository.save(note);
    }

    private boolean isPdfFile(String originalFilename) {
        String filename = originalFilename != null ? originalFilename : "";
        return filename.toLowerCase().endsWith(".pdf");
    }

    @GetMapping("/search")
    public ResponseEntity<List<SearchResult>> searchNotes(@RequestParam("q") String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return ResponseEntity.ok(List.of());
        }
        
        final String searchKeyword = keyword.toLowerCase();

        // Perform the requested SQL LIKE search via JPA Method
        List<Note> allCompletedNotes = noteRepository.findByContentContainingIgnoreCase(searchKeyword);

        List<SearchResult> results = allCompletedNotes.stream()
                .filter(note -> "COMPLETED".equals(note.getStatus()))
                .map(note -> {
                    String snippet = generateSnippet(note.getContent(), searchKeyword, 80);
                    return new SearchResult(note.getFilename(), snippet, note.getOriginalFilename(), note.getContent());
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(results);
    }

    @GetMapping("/all")
    public ResponseEntity<List<SearchResult>> fetchAllNotes() {
        List<Note> allNotes = noteRepository.findAll();
        List<SearchResult> results = allNotes.stream()
                .map(note -> {
                    String snippet = note.getContent() != null && !note.getContent().isBlank()
                            ? (note.getContent().length() > 160 ? note.getContent().substring(0, 160) + "..." : note.getContent())
                            : "Status: " + note.getStatus();
                    return new SearchResult(note.getFilename(), snippet, note.getOriginalFilename(), note.getContent() == null ? "" : note.getContent());
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(results);
    }

    @GetMapping("/status")
    public ResponseEntity<OcrService.ProcessingStatus> getProcessingStatus() {
        return ResponseEntity.ok(ocrService.getProcessingStatus());
    }

    private String generateSnippet(String fullText, String keyword, int padding) {
        int idx = fullText.toLowerCase().indexOf(keyword);
        if (idx == -1) return fullText;
        
        int start = Math.max(0, idx - padding);
        int end = Math.min(fullText.length(), idx + keyword.length() + padding);
        
        String snippet = fullText.substring(start, end).replace("\n", " ");
        if (start > 0) snippet = "..." + snippet;
        if (end < fullText.length()) snippet = snippet + "...";
        
        return snippet;
    }

    @Data
    public static class UploadResult {
        private final String id;
        private final String originalFilename;
        private final String status;
    }

    @Data
    public static class BulkUploadResponse {
        private final List<UploadResult> results;
    }

    @Data
    public static class SearchResult {
        private final String imageUrl;
        private final String snippet;
        private final String filename;
        private final String fullContent;
    }
}
