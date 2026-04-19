package com.vault.domain;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class NoteFileStore {

    private final Path notesFilePath;
    private final ObjectMapper objectMapper;
    private final Object fileLock = new Object();

    public NoteFileStore(@Value("${app.note-store.file:notes.json}") String notesFilePath) {
        this.notesFilePath = Paths.get(notesFilePath);
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @PostConstruct
    public void ensureStoreExists() {
        synchronized (fileLock) {
            try {
                Files.createDirectories(notesFilePath.getParent() == null ? Path.of(".") : notesFilePath.getParent());
                if (Files.notExists(notesFilePath)) {
                    writeNotes(new LinkedHashMap<>());
                }
            } catch (IOException e) {
                log.error("Unable to initialize note file store at {}", notesFilePath, e);
            }
        }
    }

    public List<Note> loadNotes() {
        return new java.util.ArrayList<>(loadNotesMap().values());
    }

    public Optional<Note> loadNoteById(String noteId) {
        return Optional.ofNullable(loadNotesMap().get(noteId));
    }

    public void saveNoteToFile(Note note) {
        synchronized (fileLock) {
            Map<String, Note> notes = loadNotesMap();
            notes.put(note.getId(), note);
            writeNotes(notes);
        }
    }

    public void updateNoteStatus(String noteId, String status) {
        synchronized (fileLock) {
            Map<String, Note> notes = loadNotesMap();
            Note note = notes.get(noteId);
            if (note != null) {
                note.setStatus(status);
                writeNotes(notes);
            } else {
                log.warn("Note {} not found in file store when updating status", noteId);
            }
        }
    }

    private Map<String, Note> loadNotesMap() {
        synchronized (fileLock) {
            if (Files.notExists(notesFilePath)) {
                return new LinkedHashMap<>();
            }

            try {
                List<Note> notes = objectMapper.readValue(notesFilePath.toFile(), new TypeReference<>() {
                });
                return notes.stream().collect(Collectors.toMap(Note::getId, note -> note, (a, b) -> b, LinkedHashMap::new));
            } catch (IOException e) {
                log.error("Failed to read note file store from {}", notesFilePath, e);
                return new LinkedHashMap<>();
            }
        }
    }

    private void writeNotes(Map<String, Note> notes) {
        synchronized (fileLock) {
            try {
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(notesFilePath.toFile(), notes.values());
            } catch (IOException e) {
                log.error("Failed to write note file store to {}", notesFilePath, e);
            }
        }
    }
}
