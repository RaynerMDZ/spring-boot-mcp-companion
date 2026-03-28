// Documentation structure with correct paths
const docStructure = {
    sections: [
        {
            title: 'Getting Started',
            items: [
                { title: 'Quick Start', path: 'README', id: 'quick-start', file: 'README.md' },
                { title: 'Installation Guide', path: 'getting-started/QUICK_START', id: 'installation', file: 'getting-started/QUICK_START.md' },
                { title: 'Project Overview', path: 'getting-started/README', id: 'overview', file: 'getting-started/README.md' },
            ]
        },
        {
            title: 'Core API',
            items: [
                { title: 'API Reference', path: 'core/API_REFERENCE', id: 'api-ref', file: 'core/API_REFERENCE.md' },
                { title: 'Features', path: 'core/FEATURES', id: 'features', file: 'core/FEATURES.md' },
                { title: 'Examples', path: 'core/EXAMPLES', id: 'examples', file: 'core/EXAMPLES.md' },
            ]
        },
        {
            title: 'Advanced',
            items: [
                { title: 'Architecture', path: 'ARCHITECTURE', id: 'architecture', file: 'ARCHITECTURE.md' },
                { title: 'Custom Objects', path: 'CUSTOM_OBJECTS', id: 'custom-objects', file: 'CUSTOM_OBJECTS.md' },
                { title: 'MCP Specification', path: 'MCP_SPECIFICATION', id: 'mcp-spec', file: 'MCP_SPECIFICATION.md' },
            ]
        },
        {
            title: 'Production',
            items: [
                { title: 'Best Practices', path: 'production/BEST_PRACTICES', id: 'best-practices', file: 'production/BEST_PRACTICES.md' },
                { title: 'Security Guide', path: 'production/SECURITY', id: 'security', file: 'production/SECURITY.md' },
                { title: 'Advanced Configuration', path: 'production/ADVANCED', id: 'advanced', file: 'production/ADVANCED.md' },
                { title: 'Troubleshooting', path: 'production/TROUBLESHOOTING', id: 'troubleshooting', file: 'production/TROUBLESHOOTING.md' },
            ]
        },
        {
            title: 'Contributing',
            items: [
                { title: 'Contributing Guidelines', path: 'contributing/CONTRIBUTING', id: 'contributing', file: 'contributing/CONTRIBUTING.md' },
                { title: 'Changelog', path: 'contributing/CHANGELOG', id: 'changelog', file: 'contributing/CHANGELOG.md' },
                { title: 'License Analysis', path: 'contributing/LICENSE_ANALYSIS', id: 'license', file: 'contributing/LICENSE_ANALYSIS.md' },
            ]
        }
    ]
};

// App State
const app = {
    currentPage: null,
    documentCache: new Map(),
    allDocuments: [],
    searchIndex: [],
    isDarkMode: localStorage.getItem('darkMode') === 'true',
    baseUrl: '../' // Base URL for loading markdown files from docs/web/
};

// Initialize
document.addEventListener('DOMContentLoaded', () => {
    initializeTheme();
    buildNavigation();
    setupEventListeners();
    showHeroSection();
    buildSearchIndex();
});

/**
 * Initialize theme
 */
function initializeTheme() {
    if (app.isDarkMode) {
        document.body.classList.add('dark-mode');
    }
    updateThemeIcon();
}

/**
 * Update theme icon
 */
function updateThemeIcon() {
    const toggle = document.getElementById('themeToggle');
    toggle.innerHTML = app.isDarkMode
        ? '<svg class="theme-icon" viewBox="0 0 24 24" fill="currentColor"><path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"></path></svg>'
        : '<svg class="theme-icon" viewBox="0 0 24 24" fill="currentColor"><path d="M12 3v1m0 16v1m9-9h-1M4 12H3m15.364 6.364l-.707-.707M6.343 6.343l-.707-.707m12.728 0l-.707.707M6.343 17.657l-.707.707M16 12a4 4 0 11-8 0 4 4 0 018 0z"></path></svg>';
}

/**
 * Build navigation from docStructure
 */
function buildNavigation() {
    const navMenu = document.querySelector('.sidebar-nav');
    navMenu.innerHTML = '';

    docStructure.sections.forEach(section => {
        const navSection = document.createElement('div');
        navSection.className = 'nav-section';

        // Section title
        const sectionTitle = document.createElement('div');
        sectionTitle.className = 'nav-section-title';
        sectionTitle.textContent = section.title;
        navSection.appendChild(sectionTitle);

        // Section items
        const ul = document.createElement('ul');
        ul.className = 'nav-list';

        section.items.forEach(item => {
            const li = document.createElement('li');
            li.className = 'nav-item';

            const link = document.createElement('a');
            link.className = 'nav-link';
            link.textContent = item.title;
            link.href = '#';
            link.dataset.id = item.id;
            link.dataset.file = item.file;

            link.addEventListener('click', (e) => {
                e.preventDefault();
                closeSidebar();
                loadPage(item.id, item.file, item.title);
            });

            li.appendChild(link);
            ul.appendChild(li);

            // Store for search
            app.allDocuments.push({
                id: item.id,
                title: item.title,
                file: item.file,
                section: section.title
            });
        });

        navSection.appendChild(ul);
        navMenu.appendChild(navSection);
    });
}

/**
 * Setup event listeners
 */
function setupEventListeners() {
    // Theme toggle
    document.getElementById('themeToggle').addEventListener('click', toggleTheme);

    // Mobile menu
    document.getElementById('mobileMenuBtn').addEventListener('click', toggleSidebar);

    // Search
    const searchInput = document.getElementById('searchInput');
    const searchModal = document.getElementById('searchModal');

    searchInput.addEventListener('focus', () => {
        if (searchInput.value.trim()) {
            openSearchModal();
        }
    });

    searchInput.addEventListener('input', debounce(handleSearch, 300));

    // Search modal
    document.getElementById('searchModalInput').addEventListener('input', debounce(handleSearchModal, 300));
    document.querySelector('.search-close').addEventListener('click', closeSearchModal);
    document.querySelector('.search-modal-overlay').addEventListener('click', closeSearchModal);

    // Navigation buttons
    document.getElementById('prevBtn').addEventListener('click', goToPrevious);
    document.getElementById('nextBtn').addEventListener('click', goToNext);

    // Keyboard shortcuts
    document.addEventListener('keydown', handleKeyboardShortcuts);
}

/**
 * Toggle theme
 */
function toggleTheme() {
    app.isDarkMode = !app.isDarkMode;
    document.body.classList.toggle('dark-mode');
    localStorage.setItem('darkMode', app.isDarkMode);
    updateThemeIcon();
}

/**
 * Toggle sidebar on mobile
 */
function toggleSidebar() {
    document.getElementById('sidebar').classList.toggle('active');
}

/**
 * Close sidebar
 */
function closeSidebar() {
    document.getElementById('sidebar').classList.remove('active');
}

/**
 * Show hero section
 */
function showHeroSection() {
    const heroSection = document.getElementById('heroSection');
    const docContainer = document.querySelector('.doc-container');
    heroSection.style.display = 'block';
    docContainer.style.display = 'none';
}

/**
 * Hide hero section
 */
function hideHeroSection() {
    const heroSection = document.getElementById('heroSection');
    const docContainer = document.querySelector('.doc-container');
    heroSection.style.display = 'none';
    docContainer.style.display = 'block';
}

/**
 * Load first page
 */
function loadFirstPage() {
    const firstDoc = docStructure.sections[0].items[0];
    loadPage(firstDoc.id, firstDoc.file, firstDoc.title);
}

/**
 * Load a documentation page
 */
async function loadPage(id, filePath, title) {
    hideHeroSection();
    const contentDiv = document.getElementById('docContent');
    contentDiv.innerHTML = '<div class="loading"><div class="spinner"></div><p>Loading documentation...</p></div>';

    try {
        // Construct full path
        const fullPath = app.baseUrl + filePath;

        // Get markdown content
        let content = app.documentCache.get(fullPath);
        if (!content) {
            const response = await fetch(fullPath);
            if (!response.ok) {
                throw new Error(`Failed to load ${filePath} (404)`);
            }
            content = await response.text();
            app.documentCache.set(fullPath, content);
        }

        // Convert markdown to HTML
        const html = marked.parse(content);
        contentDiv.innerHTML = html;

        // Highlight code blocks
        document.querySelectorAll('pre code').forEach(block => {
            hljs.highlightElement(block);
        });

        // Update current page
        app.currentPage = { id, filePath, title };

        // Update UI
        updateBreadcrumb(title);
        updateActiveNavLink(id);
        updateNavigationButtons();
        updateEditLink(filePath);
        updateDocMeta();

        // Scroll to top
        window.scrollTo(0, 0);

    } catch (error) {
        console.error('Error loading page:', error);
        contentDiv.innerHTML = `
            <div style="padding: 2rem; text-align: center;">
                <h2>Error Loading Documentation</h2>
                <p>${error.message}</p>
                <p style="font-size: 0.875rem; color: var(--text-secondary); margin-top: 1rem;">
                    Check that the file exists at: <code>${app.baseUrl}${filePath}</code>
                </p>
            </div>
        `;
    }
}

/**
 * Update breadcrumb
 */
function updateBreadcrumb(title) {
    const breadcrumb = document.getElementById('breadcrumb');
    breadcrumb.innerHTML = `
        <span class="breadcrumb-item"><a href="#" onclick="showHeroSection(); return false;">Documentation</a></span>
        <span class="breadcrumb-item">${title}</span>
    `;
}

/**
 * Update active nav link
 */
function updateActiveNavLink(id) {
    document.querySelectorAll('.nav-link').forEach(link => {
        link.classList.remove('active');
    });
    const activeLink = document.querySelector(`[data-id="${id}"]`);
    if (activeLink) activeLink.classList.add('active');
}

/**
 * Update navigation buttons
 */
function updateNavigationButtons() {
    const allDocs = getAllDocuments();
    const currentIndex = allDocs.findIndex(doc => doc.id === app.currentPage.id);

    const prevBtn = document.getElementById('prevBtn');
    const nextBtn = document.getElementById('nextBtn');

    if (currentIndex > 0) {
        const prevDoc = allDocs[currentIndex - 1];
        prevBtn.style.display = 'flex';
        prevBtn.onclick = () => loadPage(prevDoc.id, prevDoc.file, prevDoc.title);
    } else {
        prevBtn.style.display = 'none';
    }

    if (currentIndex < allDocs.length - 1) {
        const nextDoc = allDocs[currentIndex + 1];
        nextBtn.style.display = 'flex';
        nextBtn.onclick = () => loadPage(nextDoc.id, nextDoc.file, nextDoc.title);
    } else {
        nextBtn.style.display = 'none';
    }
}

/**
 * Update edit link
 */
function updateEditLink(filePath) {
    const editLink = document.getElementById('editLink');
    const githubUrl = `https://github.com/RaynerMDZ/spring-boot-mcp-companion/edit/main/docs/${filePath}`;
    editLink.href = githubUrl;
}

/**
 * Update doc meta
 */
function updateDocMeta() {
    const meta = document.getElementById('docMeta');
    const section = docStructure.sections.find(s => s.items.some(item => item.id === app.currentPage.id));
    if (section) {
        meta.innerHTML = `<span>${section.title}</span>`;
    }
}

/**
 * Get all documents in order
 */
function getAllDocuments() {
    const all = [];
    docStructure.sections.forEach(section => {
        section.items.forEach(item => all.push(item));
    });
    return all;
}

/**
 * Go to previous page
 */
function goToPrevious() {
    const allDocs = getAllDocuments();
    const currentIndex = allDocs.findIndex(doc => doc.id === app.currentPage.id);
    if (currentIndex > 0) {
        const prevDoc = allDocs[currentIndex - 1];
        loadPage(prevDoc.id, prevDoc.file, prevDoc.title);
    }
}

/**
 * Go to next page
 */
function goToNext() {
    const allDocs = getAllDocuments();
    const currentIndex = allDocs.findIndex(doc => doc.id === app.currentPage.id);
    if (currentIndex < allDocs.length - 1) {
        const nextDoc = allDocs[currentIndex + 1];
        loadPage(nextDoc.id, nextDoc.file, nextDoc.title);
    }
}

/**
 * Build search index
 */
async function buildSearchIndex() {
    for (const doc of app.allDocuments) {
        try {
            const fullPath = app.baseUrl + doc.file;
            let content = app.documentCache.get(fullPath);

            if (!content) {
                const response = await fetch(fullPath);
                if (response.ok) {
                    content = await response.text();
                    app.documentCache.set(fullPath, content);
                }
            }

            if (content) {
                const lines = content.split('\n');
                lines.forEach((line, idx) => {
                    if (line.match(/^#+\s+/)) {
                        const heading = line.replace(/^#+\s+/, '').trim();
                        if (heading) {
                            app.searchIndex.push({
                                title: heading,
                                docTitle: doc.title,
                                docId: doc.id,
                                docFile: doc.file,
                                section: doc.section,
                                preview: lines.slice(idx + 1, idx + 3).join(' ').substring(0, 100)
                            });
                        }
                    }
                });
            }
        } catch (error) {
            console.warn(`Could not index ${doc.title}`);
        }
    }
}

/**
 * Handle search in navbar
 */
function handleSearch(e) {
    const query = e.target.value.trim();

    if (!query) {
        document.getElementById('searchInput').value = '';
        return;
    }

    openSearchModal();
    document.getElementById('searchModalInput').value = query;
    performSearch(query);
}

/**
 * Handle search in modal
 */
function handleSearchModal(e) {
    const query = e.target.value.trim();
    performSearch(query);
}

/**
 * Perform search
 */
function performSearch(query) {
    const results = [];
    const searchTerms = query.toLowerCase().split(/\s+/).filter(t => t);

    if (!searchTerms.length) {
        document.getElementById('searchResults').innerHTML = '';
        return;
    }

    // Search in documents
    app.allDocuments.forEach(doc => {
        const titleMatch = searchTerms.some(term => doc.title.toLowerCase().includes(term));
        const sectionMatch = searchTerms.some(term => doc.section.toLowerCase().includes(term));

        if (titleMatch || sectionMatch) {
            results.push({
                title: doc.title,
                section: doc.section,
                id: doc.id,
                file: doc.file,
                preview: doc.section,
                score: titleMatch ? 2 : 1
            });
        }
    });

    // Search in index (headings)
    app.searchIndex.forEach(item => {
        const headingMatch = searchTerms.some(term => item.title.toLowerCase().includes(term));
        if (headingMatch && !results.find(r => r.id === item.docId)) {
            results.push({
                title: `${item.docTitle} - ${item.title}`,
                section: item.docTitle,
                id: item.docId,
                file: item.docFile,
                preview: item.preview || item.section,
                score: 1
            });
        }
    });

    // Sort by score and remove duplicates
    const unique = new Map();
    results.sort((a, b) => b.score - a.score);
    results.forEach(r => {
        if (!unique.has(r.id)) unique.set(r.id, r);
    });

    displaySearchResults(Array.from(unique.values()));
}

/**
 * Display search results
 */
function displaySearchResults(results) {
    const resultsDiv = document.getElementById('searchResults');

    if (!results.length) {
        resultsDiv.innerHTML = '<div style="padding: 2rem; text-align: center; color: var(--text-secondary);">No results found</div>';
        return;
    }

    resultsDiv.innerHTML = results.map(result => `
        <div class="search-result-item" onclick="loadPage('${result.id}', '${result.file}', '${result.section}'); closeSearchModal();">
            <div class="search-result-title">${result.title}</div>
            <div class="search-result-preview">${result.preview}</div>
        </div>
    `).join('');
}

/**
 * Open search modal
 */
function openSearchModal() {
    document.getElementById('searchModal').classList.add('active');
}

/**
 * Close search modal
 */
function closeSearchModal() {
    document.getElementById('searchModal').classList.remove('active');
    document.getElementById('searchInput').value = '';
    document.getElementById('searchModalInput').value = '';
}

/**
 * Handle keyboard shortcuts
 */
function handleKeyboardShortcuts(e) {
    // Cmd/Ctrl + K for search
    if ((e.ctrlKey || e.metaKey) && e.key === 'k') {
        e.preventDefault();
        openSearchModal();
        document.getElementById('searchModalInput').focus();
    }

    // Escape to close search
    if (e.key === 'Escape') {
        closeSearchModal();
    }

    // Arrow keys for navigation (when not in input)
    if (!['INPUT', 'TEXTAREA'].includes(document.activeElement.tagName)) {
        if (e.key === 'ArrowLeft') {
            goToPrevious();
        } else if (e.key === 'ArrowRight') {
            goToNext();
        }
    }
}

/**
 * Debounce utility
 */
function debounce(func, wait) {
    let timeout;
    return function executedFunction(...args) {
        const later = () => {
            clearTimeout(timeout);
            func(...args);
        };
        clearTimeout(timeout);
        timeout = setTimeout(later, wait);
    };
}
