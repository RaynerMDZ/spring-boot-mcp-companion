// Documentation structure
const docStructure = {
    sections: [
        {
            title: 'Getting Started',
            items: [
                { title: 'Quick Start', path: '../README.md', id: 'quick-start' },
                { title: 'Project Overview', path: '../getting-started/README.md', id: 'overview' },
                { title: 'Installation', path: '../getting-started/QUICK_START.md', id: 'installation' },
            ]
        },
        {
            title: 'Core Documentation',
            items: [
                { title: 'API Reference', path: '../core/API_REFERENCE.md', id: 'api-ref' },
                { title: 'Features', path: '../core/FEATURES.md', id: 'features' },
                { title: 'Examples', path: '../core/EXAMPLES.md', id: 'examples' },
            ]
        },
        {
            title: 'Advanced Topics',
            items: [
                { title: 'Architecture', path: '../ARCHITECTURE.md', id: 'architecture' },
                { title: 'Custom Objects', path: '../CUSTOM_OBJECTS.md', id: 'custom-objects' },
                { title: 'MCP Specification', path: '../MCP_SPECIFICATION.md', id: 'mcp-spec' },
            ]
        },
        {
            title: 'Production',
            items: [
                { title: 'Best Practices', path: '../production/BEST_PRACTICES.md', id: 'best-practices' },
                { title: 'Security Guide', path: '../production/SECURITY.md', id: 'security' },
                { title: 'Advanced Configuration', path: '../production/ADVANCED.md', id: 'advanced' },
                { title: 'Troubleshooting', path: '../production/TROUBLESHOOTING.md', id: 'troubleshooting' },
            ]
        },
        {
            title: 'Contributing',
            items: [
                { title: 'Contributing Guidelines', path: '../contributing/CONTRIBUTING.md', id: 'contributing' },
                { title: 'Changelog', path: '../contributing/CHANGELOG.md', id: 'changelog' },
                { title: 'License Analysis', path: '../contributing/LICENSE_ANALYSIS.md', id: 'license' },
            ]
        }
    ]
};

// App state
const app = {
    currentPage: null,
    documentCache: new Map(),
    allDocuments: [],
    searchIndex: [],
    isDarkMode: localStorage.getItem('darkMode') === 'true'
};

// Initialize app
document.addEventListener('DOMContentLoaded', () => {
    initializeTheme();
    buildNavigation();
    setupEventListeners();
    loadInitialPage();
    buildSearchIndex();
});

/**
 * Initialize theme based on user preference
 */
function initializeTheme() {
    if (app.isDarkMode) {
        document.body.classList.add('dark-mode');
        updateThemeToggle();
    }
}

/**
 * Update theme toggle button
 */
function updateThemeToggle() {
    const toggle = document.getElementById('themeToggle');
    toggle.textContent = app.isDarkMode ? '☀️' : '🌙';
}

/**
 * Build navigation menu from docStructure
 */
function buildNavigation() {
    const navMenu = document.querySelector('.nav-menu');
    navMenu.innerHTML = '';

    docStructure.sections.forEach(section => {
        // Section title
        const sectionTitle = document.createElement('div');
        sectionTitle.className = 'nav-section-title';
        sectionTitle.textContent = section.title;
        navMenu.appendChild(sectionTitle);

        // Section items
        section.items.forEach(item => {
            const li = document.createElement('li');
            li.className = 'nav-item';

            const link = document.createElement('a');
            link.className = 'nav-link';
            link.textContent = item.title;
            link.href = '#';
            link.dataset.id = item.id;
            link.dataset.path = item.path;

            link.addEventListener('click', (e) => {
                e.preventDefault();
                loadPage(item.id, item.path, item.title);
            });

            li.appendChild(link);
            navMenu.appendChild(li);

            // Store for search
            app.allDocuments.push({
                id: item.id,
                title: item.title,
                path: item.path,
                section: section.title
            });
        });
    });
}

/**
 * Setup event listeners
 */
function setupEventListeners() {
    // Theme toggle
    document.getElementById('themeToggle').addEventListener('click', toggleTheme);

    // Search
    const searchInput = document.getElementById('searchInput');
    searchInput.addEventListener('input', debounce(handleSearch, 300));

    // Search modal close
    document.querySelector('.close-modal').addEventListener('click', closeSearchModal);
    document.getElementById('searchModal').addEventListener('click', (e) => {
        if (e.target === document.getElementById('searchModal')) {
            closeSearchModal();
        }
    });

    // Mobile menu toggle
    document.getElementById('menuToggle').addEventListener('click', toggleSidebar);

    // Navigation buttons
    document.getElementById('prevBtn').addEventListener('click', goToPrevious);
    document.getElementById('nextBtn').addEventListener('click', goToNext);

    // Close sidebar when clicking on a link (mobile)
    document.querySelectorAll('.nav-link').forEach(link => {
        link.addEventListener('click', () => {
            if (window.innerWidth <= 768) {
                document.querySelector('.sidebar').classList.remove('active');
            }
        });
    });
}

/**
 * Toggle dark mode
 */
function toggleTheme() {
    app.isDarkMode = !app.isDarkMode;
    document.body.classList.toggle('dark-mode');
    localStorage.setItem('darkMode', app.isDarkMode);
    updateThemeToggle();
}

/**
 * Toggle sidebar on mobile
 */
function toggleSidebar() {
    document.querySelector('.sidebar').classList.toggle('active');
}

/**
 * Load initial page
 */
function loadInitialPage() {
    const firstDoc = docStructure.sections[0].items[0];
    loadPage(firstDoc.id, firstDoc.path, firstDoc.title);
}

/**
 * Load a documentation page
 */
async function loadPage(id, path, title) {
    const contentDiv = document.getElementById('docContent');
    const breadcrumb = document.getElementById('breadcrumb');

    try {
        contentDiv.innerHTML = '<div class="loading">Loading documentation...</div>';

        // Get markdown content
        let content = app.documentCache.get(path);
        if (!content) {
            const response = await fetch(path);
            if (!response.ok) throw new Error(`Failed to load ${path}`);
            content = await response.text();
            app.documentCache.set(path, content);
        }

        // Convert markdown to HTML
        const html = marked.parse(content);
        contentDiv.innerHTML = html;

        // Highlight code blocks
        document.querySelectorAll('pre code').forEach(block => {
            hljs.highlightElement(block);
        });

        // Update current page
        app.currentPage = { id, path, title };

        // Update breadcrumb
        updateBreadcrumb(title);

        // Update active nav link
        document.querySelectorAll('.nav-link').forEach(link => {
            link.classList.remove('active');
        });
        const activeLink = document.querySelector(`[data-id="${id}"]`);
        if (activeLink) activeLink.classList.add('active');

        // Update navigation buttons
        updateNavigationButtons();

        // Scroll to top
        window.scrollTo(0, 0);

    } catch (error) {
        console.error('Error loading page:', error);
        contentDiv.innerHTML = `<div class="error"><h2>Error Loading Documentation</h2><p>${error.message}</p></div>`;
    }
}

/**
 * Update breadcrumb navigation
 */
function updateBreadcrumb(title) {
    const breadcrumb = document.getElementById('breadcrumb');
    breadcrumb.innerHTML = `
        <span class="breadcrumb-item">
            <a href="#" onclick="loadInitialPage(); return false;">Home</a>
        </span>
        <span class="breadcrumb-item">${title}</span>
    `;
}

/**
 * Update previous/next navigation buttons
 */
function updateNavigationButtons() {
    const prevBtn = document.getElementById('prevBtn');
    const nextBtn = document.getElementById('nextBtn');
    const allDocs = [];

    docStructure.sections.forEach(section => {
        section.items.forEach(item => allDocs.push(item));
    });

    const currentIndex = allDocs.findIndex(doc => doc.id === app.currentPage.id);

    if (currentIndex > 0) {
        const prevDoc = allDocs[currentIndex - 1];
        prevBtn.style.display = 'block';
        prevBtn.onclick = () => loadPage(prevDoc.id, prevDoc.path, prevDoc.title);
    } else {
        prevBtn.style.display = 'none';
    }

    if (currentIndex < allDocs.length - 1) {
        const nextDoc = allDocs[currentIndex + 1];
        nextBtn.style.display = 'block';
        nextBtn.onclick = () => loadPage(nextDoc.id, nextDoc.path, nextDoc.title);
    } else {
        nextBtn.style.display = 'none';
    }
}

/**
 * Navigate to previous page
 */
function goToPrevious() {
    const allDocs = [];
    docStructure.sections.forEach(section => {
        section.items.forEach(item => allDocs.push(item));
    });

    const currentIndex = allDocs.findIndex(doc => doc.id === app.currentPage.id);
    if (currentIndex > 0) {
        const prevDoc = allDocs[currentIndex - 1];
        loadPage(prevDoc.id, prevDoc.path, prevDoc.title);
    }
}

/**
 * Navigate to next page
 */
function goToNext() {
    const allDocs = [];
    docStructure.sections.forEach(section => {
        section.items.forEach(item => allDocs.push(item));
    });

    const currentIndex = allDocs.findIndex(doc => doc.id === app.currentPage.id);
    if (currentIndex < allDocs.length - 1) {
        const nextDoc = allDocs[currentIndex + 1];
        loadPage(nextDoc.id, nextDoc.path, nextDoc.title);
    }
}

/**
 * Build search index
 */
async function buildSearchIndex() {
    for (const doc of app.allDocuments) {
        try {
            let content = app.documentCache.get(doc.path);
            if (!content) {
                const response = await fetch(doc.path);
                if (response.ok) {
                    content = await response.text();
                    app.documentCache.set(doc.path, content);
                }
            }

            if (content) {
                // Extract headings and content for search
                const lines = content.split('\n');
                lines.forEach(line => {
                    if (line.match(/^#+\s+/)) {
                        const heading = line.replace(/^#+\s+/, '');
                        app.searchIndex.push({
                            title: heading,
                            docTitle: doc.title,
                            docId: doc.id,
                            docPath: doc.path,
                            section: doc.section
                        });
                    }
                });
            }
        } catch (error) {
            console.warn(`Could not index ${doc.title}:`, error);
        }
    }
}

/**
 * Handle search
 */
function handleSearch(e) {
    const query = e.target.value.toLowerCase().trim();

    if (!query) {
        closeSearchModal();
        return;
    }

    const results = [];
    const searchTerms = query.split(/\s+/);

    app.allDocuments.forEach(doc => {
        const titleMatch = searchTerms.some(term => doc.title.toLowerCase().includes(term));
        const sectionMatch = searchTerms.some(term => doc.section.toLowerCase().includes(term));

        if (titleMatch || sectionMatch) {
            results.push({
                title: doc.title,
                section: doc.section,
                id: doc.id,
                path: doc.path,
                preview: doc.section
            });
        }
    });

    // Also search in index (headings)
    app.searchIndex.forEach(item => {
        const headingMatch = searchTerms.some(term => item.title.toLowerCase().includes(term));
        if (headingMatch && !results.find(r => r.id === item.docId)) {
            results.push({
                title: `${item.docTitle} - ${item.title}`,
                section: item.docTitle,
                id: item.docId,
                path: item.docPath,
                preview: item.section
            });
        }
    });

    displaySearchResults(results);
    openSearchModal();
}

/**
 * Display search results
 */
function displaySearchResults(results) {
    const resultsDiv = document.getElementById('searchResults');

    if (results.length === 0) {
        resultsDiv.innerHTML = '<p style="color: var(--text-secondary);">No results found</p>';
        return;
    }

    resultsDiv.innerHTML = results.map(result => `
        <div class="search-result-item" onclick="loadPage('${result.id}', '${result.path}', '${result.section}'); closeSearchModal();">
            <div class="search-result-title">${result.title}</div>
            <div class="search-result-preview">${result.section}</div>
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

// Keyboard shortcuts
document.addEventListener('keydown', (e) => {
    // Cmd/Ctrl + K for search
    if ((e.ctrlKey || e.metaKey) && e.key === 'k') {
        e.preventDefault();
        document.getElementById('searchInput').focus();
    }

    // Escape to close search modal
    if (e.key === 'Escape') {
        closeSearchModal();
    }

    // Arrow keys for navigation (when not in input)
    if (document.activeElement.tagName !== 'INPUT') {
        if (e.key === 'ArrowLeft') {
            goToPrevious();
        } else if (e.key === 'ArrowRight') {
            goToNext();
        }
    }
});
