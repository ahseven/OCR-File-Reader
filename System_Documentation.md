# System Documentation

## Overview

OCR File Reader is a Spring Boot web application for uploading handwritten images and PDF files, extracting text using Tesseract, saving note metadata to SQLite, and searching saved content through a browser UI.

The system is designed for clarity, resilience, and easy local setup:
- modular Java packages for controllers, services, domain, and configuration
- asynchronous OCR processing with serialized SQLite updates
- browser-based search and status monitoring
- local persistence with both `vault.db` and `notes.json`

---

## Architecture

### Entry point
- `com.vault.OcrFileReaderApplication`
- Starts Spring Boot and enables async processing

### Packages
- `com.vault.controller`  REST endpoints
- `com.vault.service`  image processing, OCR, preprocessing
- `com.vault.domain`  entity, repository, JSON file store
- `com.vault.config`  resource mapping
- `src/main/resources/static`  frontend UI assets

---

## Core components

### NoteController
- Handles file uploads, bulk uploads, search, and status endpoints
- Upload endpoints:
  - `POST /api/notes/upload`
  - `POST /api/notes/upload-multiple`
- Search/status endpoints:
  - `GET /api/notes/search?q=...`
  - `GET /api/notes/all`
  - `GET /api/notes/status`
- Workflow:
  1. create a `Note` with `status = PROCESSING`
  2. process and save an optimized image
  3. persist note to SQLite and `notes.json`
  4. start OCR asynchronously
  5. return note metadata immediately

### ImageProcessingService
- Prepares uploads for OCR
- Handles both images and PDF first pages
- Resizes large images, converts to grayscale, enhances contrast
- Saves optimized PNG files in `processed-notes`

### ImagePreProcessor
- Cleans OCR inputs before Tesseract
- Ensures RGB compatibility
- Applies grayscale conversion and contrast stretching
- Writes temporary PNG files ready for OCR

### OcrService
- Runs OCR work asynchronously with Tess4J
- Uses a CPU-sized thread pool for OCR tasks
- Uses a single-threaded DB executor to serialize SQLite updates
- Downloads `tessdata/eng.traineddata` if missing
- Supports image and multi-page PDF OCR
- Updates note status to `COMPLETED` or `FAILED`

### NoteFileStore
- Secondary JSON persistence layer
- Stores `notes.json` alongside SQLite
- Uses Jackson with `JavaTimeModule`
- Maintains thread-safe file updates

### NoteRepository
- Extends `JpaRepository<Note, String>`
- Custom search method: `findByContentContainingIgnoreCase(String keyword)`

### WebConfig
- Maps `/processed-notes/**` to the local `processed-notes` directory
- Enables browser access to processed image preview URLs

---

## Data flow

### Upload flow
1. Browser uploads a file to `POST /api/notes/upload`
2. `NoteController` creates a `PROCESSING` note
3. `ImageProcessingService` normalizes and saves a PNG
4. The note is saved to SQLite and `notes.json`
5. `OcrService` begins OCR in the background
6. The API returns immediately while OCR continues

### OCR flow
1. `OcrService` dispatches OCR work to a thread pool
2. PDFs are rendered page-by-page; images are preprocessed
3. `ImagePreProcessor` improves image quality for OCR
4. Tesseract extracts text into the note
5. Results are queued and saved to SQLite and `notes.json`

### Search flow
- `GET /api/notes/search?q=...` queries the SQLite repository
- Only notes with `status == COMPLETED` are returned
- Results include preview image URLs and text snippets

---

## Configuration

### `src/main/resources/application.properties`
- `spring.application.name=ocr-file-reader`
- `server.port=8080`

#### SQLite
- `spring.datasource.url=jdbc:sqlite:vault.db?busy_timeout=10000&journal_mode=WAL`
- `spring.datasource.driver-class-name=org.sqlite.JDBC`
- `spring.jpa.database-platform=org.hibernate.community.dialect.SQLiteDialect`
- `spring.datasource.hikari.maximum-pool-size=1`
- `spring.datasource.hikari.minimum-idle=1`
- `spring.datasource.hikari.connection-timeout=30000`
- `spring.datasource.hikari.idle-timeout=600000`

#### Hibernate
- `spring.jpa.hibernate.ddl-auto=update`
- `spring.jpa.show-sql=false`

#### Upload and thread settings
- `spring.servlet.multipart.max-file-size=50MB`
- `spring.servlet.multipart.max-request-size=1GB`
- `spring.threads.virtual.enabled=true`

#### Application settings
- `app.tessdata.url=https://github.com/tesseract-ocr/tessdata_best/raw/main/eng.traineddata`
- `app.tessdata.dir=tessdata`
- `app.upload.dir=processed-notes`
- `app.note-store.file=notes.json`

---

## Files and persistence

- `vault.db`  primary SQLite store
- `notes.json`  secondary note store backup
- `processed-notes/`  generated PNG files for preview
- `tessdata/eng.traineddata`  OCR language model

Notes:
- The app creates missing database and tessdata files automatically.
- `processed-notes` files are served by Spring via `WebConfig`.

---

## Frontend overview

- UI assets live in `src/main/resources/static`
- Supports drag/drop, file selection, and paste uploads
- Search input is debounced for smoother UX
- Status panel shows OCR queue, DB queue, and task counts
- Search results display snippets and full OCR text on demand

---

## Run and build

### Start locally
```powershell
./mvnw.cmd spring-boot:run
```

Open:

`http://localhost:8080`

### Package
```powershell
./mvnw.cmd clean package
java -jar target/*.jar
```

---

## Design summary

- Separates upload processing from OCR execution for responsiveness
- Handles image and PDF input with improved preprocessing
- Protects SQLite with serialized writes and retry behavior
- Maintains a JSON backup store for additional resilience
- Includes the Maven wrapper for fresh clone usability

---

## Key benefits

- Fresh-clone runnable with included wrapper
- Searchable OCR results in a browser UI
- Local SQL and JSON persistence
- Safe, asynchronous OCR execution
- PDF and image support in one workflow
