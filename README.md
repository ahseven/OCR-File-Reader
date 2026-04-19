# 📟 Handwritten Note Vault

A Spring Boot application for uploading handwritten images and PDFs, extracting text with OCR, and searching saved note content.

This repo is configured for GitHub deployment and includes instructions for running the app out of the box.

---

## 🚀 Quick Start

### Prerequisites
- Java 21 or later
- Git
- No local Maven install required — the included Maven wrapper works out of the box

### Clone the repository
```powershell
git clone https://github.com/ahseven/OCR-File-Reader.git
cd Java-OCR-search
```

### Run the app
```powershell
./mvnw spring-boot:run
```

Then open:

`http://localhost:8080`

---

## 🧰 What’s included
- Spring Boot backend with REST endpoints for uploading and searching notes
- OCR image processing using Tesseract-compatible data
- SQLite database support via `vault.db`
- File-based persistence support for note metadata
- Static frontend files under `src/main/resources/static`

---

## 📁 Database notes
The app uses SQLite with `vault.db` in the project root.

- `vault.db` should generally not be committed to Git because it is a local database file
- `.gitignore` is set to ignore `vault.db`, `vault.db-shm`, and `vault.db-wal`
- If you want the app to work immediately after cloning, you can manually place an empty `vault.db` in the root before running
- If `vault.db` is missing, Spring Boot will create it automatically on first launch

> If you upload an empty `vault.db` to GitHub manually, ensure your local `git` workflow does not accidentally commit a real database file.

---

## ✅ Run as a packaged JAR
Build the project:
```powershell
./mvnw clean package
```

Run the generated JAR:
```powershell
java -jar target/*.jar
```

---

## 🔧 Notes for GitHub deployment
- Keep the `vault.db` SQLite file out of the repository unless it is a deliberately empty starter database
- The `.gitignore` file will prevent accidental commits of local build artifacts and database files
- Optional files such as `notes.json` may be used for file-based persistence and are not ignored by default

---

## 📚 Useful files
- `src/main/java/com/vault/controller/NoteController.java` — upload and search endpoints
- `src/main/java/com/vault/domain/Note.java` — custom note entity
- `src/main/java/com/vault/service/ImageProcessingService.java` — image preprocessing and save logic
- `src/main/resources/application.properties` — app configuration and SQLite settings
- `presentation_flow.md` — presentation outline and project fit notes

---

## 💬 Troubleshooting
- If the UI does not start, verify Java is installed and `./mvnw` has execute permission
- If OCR fails, ensure the `tessdata/eng.traineddata` file exists
- If the database file is missing, the app will create `vault.db` automatically on startup
