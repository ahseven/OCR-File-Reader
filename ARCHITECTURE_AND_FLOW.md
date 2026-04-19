# Architecture and Flow

## Architecture Diagram

```mermaid
flowchart LR
    subgraph Backend
        A[Spring Boot App] --> B[NoteController]
        B --> C[ImageProcessingService]
        B --> D[OcrService]
        B --> E[NoteRepository]
        D --> F[ImagePreProcessor]
        E --> G[SQLite Database]
        D --> H[Tess4J / Tesseract]
    end

    subgraph Frontend
        I[Browser UI] -->|POST /api/notes/upload| B
        I -->|GET /api/notes/search?q=...| B
        I -->|Fetch static assets| J[Static Files]
    end

    J[Static Files] --> I
    C --> K[processed-notes folder]
    F --> K
    K -->|served via /processed-notes/**| I
    G --> B
```

## Flowchart

```mermaid
flowchart TD
    %% Phase 1: Upload
    Start([User Interaction]) --> Upload[Drag-and-Drop / Paste Image]
    Upload --> JS_POST[Fetch POST /api/notes/upload]
    
    %% Phase 2: Controller & Prep
    JS_POST --> Controller[NoteController: Create Note Entity]
    Controller --> SQL_Init[(SQLite: Save initial metadata)]
    
    %% Phase 3: Fast Image Optimization (Sync)
    Controller --> IPS[ImageProcessingService]
    subgraph Optimization [Fast Prep]
        IPS --> Resize[Resize: Max 1200px width]
        Resize --> Gray[Convert to Grayscale]
        Gray --> Contrast[Apply RescaleOp Contrast Filter]
        Contrast --> SavePNG[Save optimized PNG to Disk]
    end
    
    %% Phase 4: Async OCR Pipeline
    SavePNG --> AsyncHand[Handoff to OcrService Thread Pool]
    AsyncHand --> PreProc[ImagePreProcessor: Deep Cleaning]
    
    subgraph OCR_Clean [Advanced Pre-Processing]
        PreProc --> Upscale[Upscale small text to target 300 DPI]
        Upscale --> Adaptive[Adaptive Thresholding: Integral Image]
        Adaptive --> Polar[Polarity Check: Dark Mode Detection]
        Polar --> Denoise[Noise Cluster Removal]
    end
    
    Denoise --> Tesseract[Tesseract LSTM Engine: PSM 3 / OEM 1]
    Tesseract --> DB_Update[(SQLite: Save extracted TEXT + Status COMPLETED)]
    
    %% Phase 5: Search Flow
    SearchIn([Search Input]) --> Debounce[300ms Debounce]
    Debounce --> SearchAPI[GET /api/notes/search?q=...]
    SearchAPI --> JPA_Query[NoteRepository: findByContentContainingIgnoreCase]
    JPA_Query --> Filter[Filter: Status == COMPLETED]
    Filter --> Snippet[Generate Snippet + Keyword Highlight]
    Snippet --> UI_Render[Render Result Cards in Browser]
```

## Notes

- The backend is a Spring Boot application with REST endpoints.
- Uploaded images are preprocessed and stored in `processed-notes`.
- OCR runs asynchronously in a background thread pool.
- Text search uses SQLite through Spring Data JPA.
- Static UI assets are served from `src/main/resources/static`.
