document.addEventListener('DOMContentLoaded', () => {
    const dropZone = document.getElementById('upload-zone');
    const fileInput = document.getElementById('file-input');
    const uploadStatus = document.getElementById('upload-status');
    const processingStatus = document.getElementById('processing-status');
    const searchInput = document.getElementById('search-input');
    const showAllButton = document.getElementById('show-all-btn');
    const resultsContainer = document.getElementById('results');
    let statusExpanded = false;
    let statusAnimated = false;

    // --- Drag & Drop logic ---
    dropZone.addEventListener('click', () => fileInput.click());

    dropZone.addEventListener('dragover', (e) => {
        e.preventDefault();
        dropZone.classList.add('dragover');
    });

    dropZone.addEventListener('dragleave', () => {
        dropZone.classList.remove('dragover');
    });

    dropZone.addEventListener('drop', (e) => {
        e.preventDefault();
        dropZone.classList.remove('dragover');
        if (e.dataTransfer.files.length) {
            uploadFiles(Array.from(e.dataTransfer.files));
        }
    });

    fileInput.addEventListener('change', () => {
        if (fileInput.files.length) {
            uploadFiles(Array.from(fileInput.files));
        }
    });

    startStatusPolling();

    // --- Clipboard Paste logic ---
    window.addEventListener('paste', (e) => {
        // Prevent accidental doubles
        e.preventDefault();
        e.stopPropagation();

        const items = e.clipboardData.items;
        for (let i = 0; i < items.length; i++) {
            if (items[i].type.startsWith('image/')) {
                const blob = items[i].getAsFile();
                // Create a descriptive name for the pasted image
                const file = new File([blob], `pasted_note_${new Date().getTime()}.png`, { type: blob.type });
                uploadFile(file);
                // Optionally show a flash or visual cue on the drop zone
                dropZone.classList.add('dragover');
                setTimeout(() => dropZone.classList.remove('dragover'), 200);
                break; // Only upload the first image found
            }
        }
    });

    function uploadFile(file) {
        const isImage = file.type.startsWith('image/');
        const isPdf = file.type === 'application/pdf';

        if (!isImage && !isPdf) {
            uploadStatus.textContent = "> ERROR: Invalid file type. Images or PDFs only.";
            return Promise.resolve({ id: null, name: file.name, success: false });
        }

        uploadStatus.textContent = `> Uploading and encrypting data for ${file.name}...`;
        return attemptUpload(file, 1);
    }

    function attemptUpload(file, attempt) {
        const formData = new FormData();
        formData.append('file', file);

        return fetch('/api/notes/upload', {
            method: 'POST',
            body: formData
        })
        .then(response => {
            if (!response.ok) throw new Error('Upload failed');
            return response.json();
        })
        .then(data => {
            uploadStatus.textContent = `> SUCCESS: Note saved as UUID [${data.id}]. Background OCR process initiated.`;
            return { id: data.id, name: file.name, success: true };
        })
        .catch(err => {
            console.error(`Upload attempt ${attempt} failed for ${file.name}:`, err);
            if (attempt < 3) {
                uploadStatus.textContent = `> Retrying ${file.name} (attempt ${attempt + 1})...`;
                return new Promise(resolve => setTimeout(resolve, 500)).then(() => attemptUpload(file, attempt + 1));
            }
            uploadStatus.textContent = `> ERROR: Processing failed for ${file.name}. Check system logs.`;
            return { id: null, name: file.name, success: false };
        });
    }

    async function uploadFiles(files) {
        if (!files || files.length === 0) return;
        if (files.length === 1) {
            await uploadFile(files[0]);
            return;
        }

        uploadStatus.textContent = `> Upload queue started for ${files.length} files...`;

        let successCount = 0;
        let failureCount = 0;

        for (let i = 0; i < files.length; i++) {
            const file = files[i];
            uploadStatus.textContent = `> Uploading ${i + 1}/${files.length}: ${file.name}...`;
            const result = await uploadFile(file);
            if (result.success) {
                successCount += 1;
            } else {
                failureCount += 1;
            }
            uploadStatus.textContent = `> Queue progress: ${successCount} success, ${failureCount} failed (${i + 1}/${files.length})`;
            if (i < files.length - 1) {
                await new Promise(resolve => setTimeout(resolve, 250));
            }
        }

        uploadStatus.textContent = `> Upload queue complete: ${successCount} uploaded, ${failureCount} failed.`;
    }

    function fetchProcessingStatus() {
        fetch('/api/notes/status')
            .then(res => res.ok ? res.json() : Promise.reject('Status fetch failed'))
            .then(data => updateProcessingStatus(data))
            .catch(err => {
                console.error('Processing status error:', err);
                processingStatus.textContent = '> Processing status unavailable.';
            });
    }

    function updateProcessingStatus(data) {
        if (!data) {
            processingStatus.textContent = '> No processing data available.';
            return;
        }

        processingStatus.innerHTML = `
            <div class="status-header${statusAnimated ? '' : ' status-animate'}">
                <div class="status-label"><strong>OCR Pool:</strong> ${data.activeOcrThreads}/${data.configuredOcrPoolSize} active</div>
                <div class="status-expand-toggle${statusExpanded ? ' expanded' : ''}">
                    <span class="expand-btn">${statusExpanded ? '▲' : '▼'}</span>
                </div>
            </div>
            <div class="status-details${statusExpanded ? '' : ' hidden'}">
                <div><strong>OCR Queue:</strong> ${data.ocrQueueSize} pending</div>
                <div><strong>DB Queue:</strong> ${data.dbQueueSize} pending</div>
                <div><strong>Submitted:</strong> ${data.totalOcrSubmitted}</div>
                <div><strong>Completed:</strong> ${data.totalOcrCompleted}</div>
                <div><strong>Failed:</strong> ${data.totalOcrFailed}</div>
            </div>
        `;
        statusAnimated = true;

        const statusExpandToggle = processingStatus.querySelector('.status-expand-toggle');
        const statusDetails = processingStatus.querySelector('.status-details');
        const expandBtn = processingStatus.querySelector('.expand-btn');

        statusExpandToggle.addEventListener('click', () => {
            statusExpanded = !statusExpanded;
            statusDetails.classList.toggle('hidden', !statusExpanded);
            expandBtn.textContent = statusExpanded ? '▲' : '▼';
            statusExpandToggle.classList.toggle('expanded', statusExpanded);
        });
    }

    function startStatusPolling() {
        fetchProcessingStatus();
        setInterval(fetchProcessingStatus, 2500);
    }

    // --- Search functionality with debounce ---
    let searchTimeout;
    let showAllActive = false;

    searchInput.addEventListener('input', (e) => {
        const keyword = e.target.value.trim();
        clearTimeout(searchTimeout);

        if (showAllActive) {
            showAllActive = false;
            showAllButton.textContent = 'SHOW ALL';
            showAllButton.classList.remove('active');
        }
        
        if (keyword.length === 0) {
            resultsContainer.innerHTML = '';
            return;
        }

        searchTimeout = setTimeout(() => {
            performSearch(keyword);
        }, 300); // 300ms debounce
    });

    showAllButton.addEventListener('click', () => {
        showAllButton.classList.add('animate');
        setTimeout(() => showAllButton.classList.remove('animate'), 350);

        if (showAllActive) {
            showAllActive = false;
            showAllButton.textContent = 'SHOW ALL';
            showAllButton.classList.remove('active');
            resultsContainer.classList.add('fade-out');
            setTimeout(() => {
                resultsContainer.innerHTML = '';
                resultsContainer.classList.remove('fade-out');
            }, 220);
            return;
        }

        searchInput.value = '';
        clearTimeout(searchTimeout);
        showAllActive = true;
        showAllButton.textContent = 'SHOW LESS';
        showAllButton.classList.add('active');
        resultsContainer.classList.add('loading');
        fetchAllNotes();
    });

    function fetchAllNotes() {
        fetch('/api/notes/all')
            .then(res => res.json())
            .then(data => {
                renderResults(data, '');
                resultsContainer.classList.remove('loading');
            })
            .catch(err => {
                console.error('Fetch all notes error:', err);
                resultsContainer.classList.remove('loading');
                resultsContainer.innerHTML = `<p>> Failed to load notes.</p>`;
            });
    }

    function performSearch(keyword) {
        fetch(`/api/notes/search?q=${encodeURIComponent(keyword)}`)
            .then(res => res.json())
            .then(data => renderResults(data, keyword))
            .catch(err => console.error('Search error:', err));
    }

    function renderResults(data, keyword) {
        resultsContainer.innerHTML = '';
        if (!data || data.length === 0) {
            resultsContainer.innerHTML = `<p>> 0 matches found.</p>`;
            return;
        }

        data.forEach(item => {
            const card = document.createElement('div');
            card.className = 'result-card';

            let highlightedSnippet = item.snippet;
            if (keyword) {
                const regex = new RegExp(`(${keyword})`, 'gi');
                highlightedSnippet = item.snippet.replace(regex, '<span class="highlight">$1</span>');
            }

            card.innerHTML = `
                <div class="expand-container">
                    <div class="expand-btn">▼</div>
                </div>
                <div class="result-main">
                    <img src="${item.imageUrl}" alt="Note Image">
                    <div class="result-text">
                        <div class="filename">FILE: ${item.filename}</div>
                        <div>> ${highlightedSnippet}</div>
                    </div>
                </div>
                <div class="full-content">
                    <div class="full-content-text"></div>
                </div>
            `;

            const fullContentText = card.querySelector('.full-content-text');
            fullContentText.textContent = `> FULL CONTENT DETECTED:\n\n${item.fullContent}`;

            const expandContainer = card.querySelector('.expand-container');
            const expandBtn = card.querySelector('.expand-btn');
            expandContainer.addEventListener('click', (e) => {
                e.stopPropagation();
                const isExpanded = card.classList.toggle('expanded');
                expandBtn.textContent = isExpanded ? '▲' : '▼';
            });

            resultsContainer.appendChild(card);
        });
    }
});
