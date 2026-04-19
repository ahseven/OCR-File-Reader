# System Documentation

## Overview

This project is a Spring Boot OCR web application that accepts image and PDF uploads, performs local OCR with Tesseract, stores extracted text in SQLite, keeps a redundant JSON note store, and exposes a dynamic searchable browser UI.

The app is built to be:
- **modular**: controllers, services, config, domain, repository, and frontend are separated cleanly
- **robust**: asynchronous OCR with serialized SQLite writes and retry handling
- **user-friendly**: drag/drop and paste uploads, live search, expandable results, and a terminal-themed UI

---

## Key components

### Application entry
- `com.vault.OcrFileReaderApplication`
- Annotated with `@SpringBootApplication`
- Boots the Spring context and starts embedded Tomcat

### Controllers
- `com.vault.controller.NoteController`
- Exposes REST endpoints and coordinates upload, search, listing, and status
- Supported endpoints:
  - `POST /api/notes/upload`
  - `POST /api/notes/upload-multiple`
  - `GET /api/notes/search?q=...`
  - `GET /api/notes/all`
  - `GET /api/notes/status`

#### Upload handling
- Creates a `Note` record immediately with `status = PROCESSING`
- Uses `ImageProcessingService` to write an optimized PNG copy into `processed-notes`
- Persists the note to the SQLite database
- Persists the note to JSON via `NoteFileStore`
- Starts async OCR with `OcrService.processOcrAsync(...)`
- Returns the note object immediately, while OCR continues in the background

#### Bulk upload support
- `POST /api/notes/upload-multiple` accepts multiple files
- Processes each file independently with the same safe workflow
- Returns a bulk response with individual upload results

#### Search and status
- `GET /api/notes/search?q=...` returns only completed notes matching the query
- `GET /api/notes/all` returns all notes, regardless of status
- `GET /api/notes/status` returns live queue and thread metrics for the UI

### Services

#### `com.vault.service.ImageProcessingService`
- Normalizes upload content before OCR
- Handles images and PDFs
- For PDFs, renders the first page at 300 DPI using Apache PDFBox
- Resizes large images to a max width of `1200px`
- Converts content to grayscale and enhances contrast
- Saves a PNG file into `processed-notes`

#### `com.vault.service.ImagePreProcessor`
- Cleans images before OCR recognition
- Forces RGB compatibility
- Converts the image to grayscale using perceptual weights
- Stretches contrast across the grayscale histogram
- Writes a cleaned temporary PNG for Tesseract

#### `com.vault.service.OcrService`
- Manages asynchronous OCR processing using Tess4J
- Uses a thread pool sized to available CPU cores for OCR work
- Uses a single-threaded DB writer executor to serialize SQLite updates
- Tracks processing metrics for the frontend
- Auto-downloads `eng.traineddata` if missing

##### OCR behavior
- Supports single images and multi-page PDFs
- For PDFs, renders every page and processes each page image
- Preprocesses each image before OCR
- Uses Tesseract with `eng` language, `PSM 3`, and `OEM 1`
- Updates notes to `COMPLETED` or `FAILED`
- Writes updates to SQLite through a retrying queue to avoid lock contention

### Domain

#### `com.vault.domain.Note`
- JPA entity stored in table `notes`
- Fields:
  - `id`: UUID string primary key
  - `filename`: saved image URL path
  - `originalFilename`: uploaded file name
  - `content`: OCR text content
  - `status`: `PROCESSING`, `COMPLETED`, or `FAILED`
  - `timestamp`: creation time

#### `com.vault.domain.NoteFileStore`
- Secondary persistence layer for note metadata
- Writes `notes.json` alongside the SQLite database
- Uses Jackson with `JavaTimeModule` for `LocalDateTime`
- Loads and updates notes in a thread-safe file-backed map
- Ensures `notes.json` exists on startup

### Repository
- `com.vault.domain.NoteRepository`
- Extends `JpaRepository<Note, String>`
- Contains `List<Note> findByContentContainingIgnoreCase(String keyword)`
- Enables case-insensitive content search in SQLite

### Web configuration
- `com.vault.config.WebConfig`
- Adds a resource handler mapping `/processed-notes/**` to the local `processed-notes` directory
- Allows browser access to processed image files via URL

---

## Configuration

### `src/main/resources/application.properties`
- `spring.application.name=ocr-file-reader`
- `server.port=8080`

#### SQLite tuning
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

#### Application-specific properties
- `app.tessdata.url=https://github.com/tesseract-ocr/tessdata_best/raw/main/eng.traineddata`
- `app.tessdata.dir=tessdata`
- `app.upload.dir=processed-notes`
- `app.note-store.file=notes.json`

---

## Static UI

### `src/main/resources/static/index.html`
- Terminal-inspired app shell
- Drag/drop file upload zone
- Click-to-upload fallback
- Clipboard paste support for image uploads
- Search input field with debounce behavior
- Expandable search results cards for full OCR text
- Status panel for live OCR and DB queue metrics
- Animated background from `background.gif`

### `src/main/resources/static/css/style.css`
- Terminal green theme on a dark animated background
- Semi-transparent `.terminal` panel for readability
- Fade-up animations on page sections
- Responsive mobile and tablet layout
- Styled upload zone, buttons, and expandable cards
- Dedicated status panel styling with an expand/collapse icon

### `src/main/resources/static/js/app.js`
- Handles drag/drop and file input upload flows
- Supports paste uploads from clipboard images
- Handles single and bulk upload queuing
- Retries failed upload attempts up to 3 times
- Polls `/api/notes/status` every 2.5 seconds
- Renders search results with keyword highlighting
- Provides expandable detail cards for full OCR text
- Keeps status expand/collapse state stable across refreshes

---

## Data flow

### Upload flow
1. The frontend sends `POST /api/notes/upload` with form data.
2. `NoteController` creates a `Note` entity as `PROCESSING`.
3. `ImageProcessingService` optimizes and saves a PNG copy.
4. The note is saved to both SQLite and `notes.json`.
5. `OcrService` starts async OCR processing.
6. The frontend displays upload progress and status text.

### OCR processing flow
1. `OcrService` submits a task to the OCR thread pool.
2. If the upload is a PDF, each page is rendered and processed.
3. If the upload is an image, the saved PNG is preprocessed.
4. `ImagePreProcessor` cleans and enhances the image.
5. Tesseract performs OCR and returns recognized text.
6. The result is scheduled for database update.

### Database update flow
- Updates occur in a single-threaded executor to protect SQLite.
- Save failures trigger retry logic on `database is locked` or `SQLITE_BUSY`.
- On success, the note becomes `COMPLETED`; on fatal error, it becomes `FAILED`.
- The note is also persisted again to `notes.json`.

---

## Advanced preprocessing

### `ImageProcessingService`
- Reduces image width to `1200px` when necessary
- Converts images to grayscale
- Applies contrast enhancement
- Writes optimized PNG files to `processed-notes`

### `ImagePreProcessor`
- Converts images to RGB-compatible `BufferedImage`
- Applies grayscale conversion using luminosity weighting
- Stretches contrast using minimum/maximum pixel scaling
- Writes cleaned PNG files for OCR input

---

## Search and results

### `GET /api/notes/search?q=...`
- Requires a non-empty query string
- Uses `findByContentContainingIgnoreCase` to search the database
- Filters only notes with `status == COMPLETED`
- Generates a snippet around the first keyword match
- Returns JSON objects with:
  - `imageUrl`
  - `snippet`
  - `filename`
  - `fullContent`

### `GET /api/notes/all`
- Returns every note record from the database
- Notes that are still processing or failed show status text instead of OCR snippets
- Useful for admin or debug views

### `GET /api/notes/status`
- Returns `OcrService.ProcessingStatus`
- Provides metrics for the status panel
- Enables live monitoring of thread and queue conditions

---

## Frontend behavior

### Upload UI
- Drag/drop and click upload are both supported
- Paste support allows direct clipboard image uploads
- Uploads are processed sequentially for stability
- Failed uploads retry automatically up to 3 times
- Status text updates with progress, success, and error messages

### Search UI
- Input changes are debounced by `300ms`
- Search requests are sent to `/api/notes/search`
- Search results are displayed as cards
- Matching keyword occurrences are highlighted
- Cards can expand to show full OCR text

### Status UI
- Polls status every 2.5 seconds
- Displays active OCR thread count, OCR queue size, DB queue size, and submit/complete/fail counts
- Uses a dedicated expand/collapse arrow control
- The UI entrance animation is separate from the expand/collapse toggle

---

## File serving
- Spring Boot serves static assets from `src/main/resources/static`
- `WebConfig` maps `/processed-notes/**` to the local `processed-notes` directory
- Uploaded OCR image previews are served via URL paths
- `notes.json` is maintained as a secondary file-backed note store

---

## Design priorities

1. **Reliability**: protect SQLite with serialized writes and retry logic
2. **Separation of concerns**: controllers, services, domain, repository, config, UI
3. **Usability**: terminal-style UI, live feedback, and responsive layout
4. **Resilience**: background OCR, safe upload processing, retry handling
5. **Redundancy**: database persistence backed by JSON note storage

---

## Run notes

- Build with `./mvnw.cmd -DskipTests compile`
- Start the app from `OcrFileReaderApplication`
- Access the app at `http://localhost:8080`
- Allow the application to download `tessdata/eng.traineddata` if it is not present
- Processed images are stored under `processed-notes`
- JSON backup is stored in `notes.json`

---

## Key benefits

- Supports large batches safely
- Avoids concurrent SQLite write conflicts
- Decouples OCR work from DB writes
- Provides live searchable UI with status monitoring
- Uses local open-source OCR tooling and SQLite

---

## End-to-end sequence

1. Spring Boot starts and configures the app.
2. Browser loads the terminal-style UI from `/index.html`.
3. User uploads or pastes a file.
4. Controller saves a `PROCESSING` note.
5. Image is preprocessed and saved to disk.
6. OCR begins in background.
7. Tesseract extracts text from the cleaned image.
8. The note update is written to SQLite.
9. The note transitions to `COMPLETED` or `FAILED`.
10. The UI polling updates the status panel.
11. Search returns matches from the database.
12. Result cards show image preview, snippet, and the full OCR text.

---

## Important details to understand

- The app is backend-driven but ships a rich browser UI from Spring static resources.
- OCR is asynchronous: upload returns before full text extraction completes.
- Search is database-backed and only returns completed notes.
- The app maintains both `vault.db` and `notes.json` for redundancy.
- Tesseract data is automatically downloaded if missing, enabling self-bootstrap behavior.

If you want, I can also produce a shorter architecture diagram or one-page flowchart from this same process.
