package com.vault.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "notes")
@Data
@NoArgsConstructor
public class Note {
    @Id
    private String id = UUID.randomUUID().toString();

    private String filename; // The path to the processed image

    private String originalFilename; // The original name of the uploaded file

    @Column(columnDefinition = "TEXT")
    private String content; // The full OCR text

    private LocalDateTime timestamp = LocalDateTime.now();
    
    // Status can be PROCESSING, COMPLETED, FAILED
    private String status = "PROCESSING";
}
