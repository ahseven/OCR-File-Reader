# Classes and Methods Documentation

## Overview
This document outlines all classes and methods in the OCR file reader system, organized by package and component. The system is a Spring Boot application for uploading, processing, and searching handwritten notes via OCR.

---

## 1. Application Entry Point

### Package: `com.vault`

#### **OcrFileReaderApplication**
Main Spring Boot application entry point with async processing enabled.

| Method | Description |
|--------|-------------|
| `main(String[] args)` | Starts the Spring Boot application |

**Annotations:**
- `@SpringBootApplication` - Spring Boot auto-configuration
- `@EnableAsync` - Enable asynchronous method execution

---

## 2. Controllers

### Package: `com.vault.controller`

#### **NoteController** (REST Endpoints)
REST controller handling file uploads and search operations.

| Method | HTTP | Description |
|--------|------|-------------|
| `uploadNote(MultipartFile file)` | POST `/api/notes/upload` | Accepts file upload, processes image/PDF, saves metadata, triggers OCR |
| `searchNotes(String keyword)` | GET `/api/notes/search?q=...` | Searches OCR content by keyword, returns snippets |
| `generateSnippet(String fullText, String keyword, int padding)` | Private | Generates text snippet around keyword occurrence |

**Inner Class:**
- `SearchResult` - DTO for search results (fields: imageUrl, snippet, filename, fullContent)

---

#### **GlobalExceptionHandler** (Error Handling)
Centralized exception handling for the entire application.

| Method | Exception Type | HTTP Status | Description |
|--------|----------------|-------------|-------------|
| `handleMaxSizeException(MaxUploadSizeExceededException exc)` | MaxUploadSizeExceededException | 413 | Handles file size limit exceeded |
| `handleIOException(IOException exc)` | IOException | 500 | Handles I/O related errors |
| `handleGenericException(Exception exc)` | Generic Exception | 500 | Catches all unhandled exceptions |

---

## 3. Services

### Package: `com.vault.service`

#### **ImageProcessingService** (Image/PDF Optimization)
Handles initial image optimization and PDF thumbnail extraction.

| Method | Visibility | Description |
|--------|-----------|-------------|
| `processAndSaveImage(MultipartFile file, UUID noteId)` | Public | Main entry point: reads file, detects PDF vs image, optimizes, saves to disk |
| `renderFirstPageOfPdf(InputStream inputStream)` | Private | Renders first page of PDF at 300 DPI for UI thumbnail |
| `ensureUploadDirectoryExists()` | Private | Creates upload directory if it doesn't exist |
| `optimizeImage(BufferedImage original)` | Private | Resize → grayscale → contrast enhancement pipeline |
| `resizeToStandardWidth(BufferedImage src, int targetWidth)` | Private | Resizes image to max 1200px width with bilinear interpolation |
| `convertToGrayscale(BufferedImage src)` | Private | Converts source image to grayscale using ColorConvertOp |
| `applyContrast(BufferedImage src, float scale, float offset)` | Private | Applies contrast stretching with RescaleOp |
| `saveImageToDisk(BufferedImage image, String filename)` | Private | Writes optimized PNG to upload directory |
| `logEfficiency(long originalSize, String filename)` | Private | Logs file size reduction percentage |

---

#### **OcrService** (Async OCR Processing)
Manages Tesseract OCR execution with multi-page PDF support and async thread pool.

| Method | Visibility | Description |
|--------|-----------|-------------|
| `OcrService(NoteRepository noteRepository, ImagePreProcessor imagePreProcessor)` | Public | Constructor: initializes fixed-size thread pool (CPU cores) |
| `init()` | PostConstruct | Downloads eng.traineddata if not present; called after bean creation |
| `downloadTessdata(File targetFile)` | Private | Downloads Tesseract language model from remote URL |
| `processOcrAsync(Note note, String filename, byte[] fileBytes, boolean isPdf)` | Public | Submits OCR task to thread pool (async entry point) |
| `performOcrTask(Note note, String filename, byte[] fileBytes, boolean isPdf)` | Private | Executes the actual OCR work on thread pool |
| `processMultiPagePdf(byte[] fileBytes, StringBuilder output)` | Private | Renders all PDF pages at 300 DPI, OCRs each, concatenates with `\n\n` |
| `processSingleImage(String filename, StringBuilder output)` | Private | Preprocesses single image file and runs OCR |
| `executeTesseract(File image)` | Private | Configures Tesseract engine (PSM=3, LSTM mode) and extracts text |
| `updateNoteSuccess(Note note, String text)` | Private | Saves OCR text to database with COMPLETED status |
| `handleOcrFailure(Note note, Exception e)` | Private | Logs error and marks note as FAILED in database |
| `cleanupTempFiles(File file)` | Private | Deletes temporary files (preprocessed images, temp PDFs) |

**Configuration Fields:**
- `tessdataDir` - Path to Tesseract language models
- `tessdataUrl` - Remote URL for downloading eng.traineddata
- `uploadDir` - Path to upload directory
- `ocrThreadPool` - ExecutorService for CPU-bound OCR tasks

---

#### **ImagePreProcessor** (Image Cleaning for OCR)
Prepares images for Tesseract by converting to grayscale and enhancing contrast.

| Method | Visibility | Description |
|--------|-----------|-------------|
| `preprocessForOcr(File inputImage)` | Public | Main entry point: RGB → grayscale → contrast enhancement → PNG output |
| `convertToGrayscale(BufferedImage src)` | Private | Converts RGB to grayscale using luminosity formula (0.299R + 0.587G + 0.114B) |
| `enhanceContrast(BufferedImage src)` | Private | Stretches pixel values from [min, max] to [0, 255] range for better OCR |

**Key Features:**
- Uses TYPE_BYTE_GRAY for Tesseract compatibility
- Generates unique temp filenames with System.nanoTime()
- Returns cleaned image file with .png extension

---

## 4. Domain Layer (Data Models & Repositories)

### Package: `com.vault.domain`

#### **Note** (JPA Entity)
Represents a single document (image or PDF) with metadata and OCR results.

| Field | Type | Description |
|-------|------|-------------|
| `id` | String | UUID primary key (auto-generated) |
| `filename` | String | Path to processed image: `/processed-notes/{uuid}.png` |
| `originalFilename` | String | Original uploaded filename |
| `content` | String (TEXT) | Full OCR extracted text |
| `timestamp` | LocalDateTime | Record creation time |
| `status` | String | State: PROCESSING / COMPLETED / FAILED |

**Lombok Annotations:** `@Data`, `@NoArgsConstructor`

---

#### **NoteRepository** (Spring Data JPA)
Data access layer for Note entities with custom query method.

| Method | Description |
|--------|-------------|
| `findByContentContainingIgnoreCase(String keyword)` | Custom query: case-insensitive partial text search |

**Inheritance:** `JpaRepository<Note, String>` provides:
- `save(Note entity)` - Insert/update operation
- `findAll()` - Retrieve all notes
- `findById(String id)` - Retrieve by UUID
- `delete(Note entity)` - Delete operation

---

## 5. Configuration

### Package: `com.vault.config`

#### **WebConfig** (Spring MVC Configuration)
Maps filesystem directories to HTTP resource handlers.

| Method | Description |
|--------|-------------|
| `addResourceHandlers(ResourceHandlerRegistry registry)` | Registers `/processed-notes/**` URL mapping to upload directory on disk |

**Configuration Fields:**
- `uploadDir` - Injected from `app.upload.dir` property

---

## 6. Data Flow & Method Interactions

### Upload Flow:
```
POST /api/notes/upload
  ↓
NoteController.uploadNote()
  ↓ Calls:
ImageProcessingService.processAndSaveImage()
  ├─ Detects PDF vs image
  ├─ (PDF) renderFirstPageOfPdf() → thumbnail for UI
  ├─ (Image) optimizeImage() → resize, grayscale, contrast
  └─ Returns filename
  ↓ Saves to DB:
Note.setFilename() + noteRepository.save()
  ↓ Submits async:
OcrService.processOcrAsync()
  ↓ (Background Thread)
OcrService.performOcrTask()
  ├─ (PDF): processMultiPagePdf()
  │   └─ Loop: renderImageWithDPI() → preprocessForOcr() → executeTesseract()
  ├─ (Image): processSingleImage()
  │   └─ preprocessForOcr() → executeTesseract()
  └─ updateNoteSuccess() or handleOcrFailure()
```

### Search Flow:
```
GET /api/notes/search?q=keyword
  ↓
NoteController.searchNotes()
  ↓
NoteRepository.findByContentContainingIgnoreCase()
  ↓ (Database)
Filter: status == "COMPLETED"
  ↓
generateSnippet() for each result
  ↓
ResponseEntity<List<SearchResult>>
```

---

## 7. Thread Safety & Concurrency

| Component | Threading Model | Details |
|-----------|-----------------|---------|
| OcrService | Fixed-size thread pool | `Executors.newFixedThreadPool(Runtime.availableProcessors())` |
| procesOcrAsync() | Async submission | Submits `performOcrTask()` to pool via `ocrThreadPool.submit()` |
| Virtual Threads | Enabled | `@EnableAsync` on main app class |
| Database | JPA managed | Spring Data JPA handles connection pooling |

---

## 8. External Dependencies Used in Methods

| Class | External Library | Methods |
|-------|------------------|---------|
| ImageProcessingService | Apache PDFBox | `renderFirstPageOfPdf()` loads PDF |
| OcrService | Apache PDFBox | `processMultiPagePdf()` renders pages |
| OcrService | Tess4J | `executeTesseract()` performs OCR |
| ImagePreProcessor | Java AWT | `convertToGrayscale()`, `enhanceContrast()` |
| Note | Jakarta Persistence | JPA annotations for entity mapping |
| All services | Lombok | `@Slf4j` for logging |

---

## 9. Property Injection (@Value)

| Class | Property | Used In |
|-------|----------|---------|
| OcrService | `app.tessdata.dir` | `init()`, `executeTesseract()` |
| OcrService | `app.tessdata.url` | `init()`, `downloadTessdata()` |
| OcrService | `app.upload.dir` | `processSingleImage()` |
| ImageProcessingService | `app.upload.dir` | `processAndSaveImage()`, `ensureUploadDirectoryExists()` |
| WebConfig | `app.upload.dir` | `addResourceHandlers()` |

---

## 10. Summary Statistics

| Category | Count |
|----------|-------|
| **Total Classes** | 7 |
| **Total Interfaces** | 1 (NoteRepository) |
| **Total Public Methods** | 11 |
| **Total Private Methods** | 30+ |
| **Total Packages** | 5 |
| **REST Endpoints** | 2 |
| **Exception Handlers** | 3 |

---

## 11. Key Design Patterns Used

| Pattern | Implementation |
|---------|----------------|
| **Builder/Fluent** | Spring builder configurations |
| **Repository** | NoteRepository abstracts data access |
| **Service Layer** | OcrService, ImageProcessingService separate concerns |
| **Global Exception Handler** | @ControllerAdvice centralized error handling |
| **Async Execution** | @Async + ExecutorService for OCR processing |
| **Dependency Injection** | Spring @Autowired, constructor injection |
| **Strategy Pattern** | PDF vs image processing branches |
| **Try-with-resources** | Automatic resource cleanup for streams and documents |

---

## 12. Status and Error Handling

### Note Status Values:
- **PROCESSING** - Initial state, OCR in progress
- **COMPLETED** - OCR succeeded, content extracted
- **FAILED** - OCR error occurred, content contains error message

### Exception Handling Chain:
1. Service level: try-catch with logging
2. Controller level: specific IOExceptions caught  
3. Global handler: @ControllerAdvice catches unhandled exceptions
4. HTTP Response: Appropriate HTTP status codes returned

---

**Document Version:** 1.0  
**Last Updated:** April 11, 2026  
**Project:** ocr-file-reader (OCR file reader Application)
