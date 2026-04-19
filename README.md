# � OCR File Reader

A Spring Boot application for uploading handwritten note images and PDF pages, extracting text with OCR, and searching saved note content.

This repository is ready to clone and run locally with the included Maven wrapper.

---

## 🚀 Quick Start

### Prerequisites
- Java 21 or later
- Git
- No local Maven installation required (the repository includes the Maven wrapper files)

### Clone the repository
```powershell
git clone https://github.com/ahseven/ocr-file-reader.git
cd ocr-file-reader
```

### Run the app
Windows PowerShell:
```powershell
./mvnw.cmd spring-boot:run
```
macOS / Linux:
```bash
./mvnw spring-boot:run
```

Open the app in your browser:

`http://localhost:8080`

---

## 🧩 What this app does
- Upload handwritten image files and PDF pages
- Preprocess images for better OCR accuracy
- Extract text using Tesseract-compatible OCR data
- Save extracted note text to a local SQLite database
- Search and display saved notes from the web UI

---

## 📁 File and database behavior
- The app uses SQLite and stores data in `vault.db` in the repository root.
- `.gitignore` is already configured to ignore:
  - `vault.db`
  - `vault.db-shm`
  - `vault.db-wal`
- If `vault.db` is missing, Spring Boot will create it automatically on first launch.

> If you want to keep an empty placeholder for a directory like `processed-notes`, add a tracked file such as `processed-notes/.gitkeep`.

---

## 🔧 Build and package
Build the project:
```powershell
./mvnw.cmd clean package
```

Run the packaged JAR:
```powershell
java -jar target/*.jar
```

---

## 📂 Important project files
- `src/main/java/com/vault/controller/NoteController.java` — upload and search API endpoints
- `src/main/java/com/vault/domain/Note.java` — note entity and persistence model
- `src/main/java/com/vault/service/ImageProcessingService.java` — image preprocessing, OCR integration, and save logic
- `src/main/java/com/vault/service/OcrService.java` — OCR wrapper and text extraction
- `src/main/resources/application.properties` — application settings and SQLite configuration
- `src/main/resources/static/index.html` — frontend UI
- `tessdata/eng.traineddata` — OCR language data for English

---

## 💬 Troubleshooting
- If the app does not start, verify Java is installed and use the correct Maven wrapper command for your OS.
- If OCR fails, confirm `tessdata/eng.traineddata` exists in the repository.
- If uploaded notes are not searchable, restart the app so the database file initializes properly.
