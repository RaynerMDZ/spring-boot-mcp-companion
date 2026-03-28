# Spring Boot MCP Companion - Documentation Website

A modern, lightweight documentation website for the Spring Boot MCP Companion project.

## Features

✨ **Modern UI**
- Clean, professional design with light and dark modes
- Responsive layout that works on desktop, tablet, and mobile
- Smooth animations and transitions

🔍 **Powerful Search**
- Full-text search across all documentation
- Real-time search results
- Keyboard shortcut: `Cmd/Ctrl + K`

⌨️ **Keyboard Navigation**
- Arrow keys to navigate between pages
- Escape to close modals
- Cmd/Ctrl + K to open search

📱 **Mobile First**
- Fully responsive design
- Touch-friendly navigation
- Optimized for all screen sizes

🚀 **Fast & Lightweight**
- No heavy frameworks or dependencies
- ~50KB total size
- Instant page loads
- Client-side rendering (no server needed)

🎨 **Easy Customization**
- CSS variables for theming
- Simple HTML structure
- Easy to add new documentation

## Quick Start

### View Locally

```bash
cd docs/web
python3 -m http.server 8000
# Visit http://localhost:8000
```

Or use any local server:
- Node.js: `npx http-server`
- Python: `python3 -m http.server 8000`
- Ruby: `ruby -run -ehttpd . -p8000`

## Project Structure

```
docs/web/
├── index.html          # Main HTML file
├── README.md          # This file
├── DEPLOYMENT.md      # Deployment guide
├── css/
│   └── style.css      # All styling
└── js/
    └── app.js         # All JavaScript logic
```

## Files Overview

### `index.html`
The main HTML file containing:
- Header with search and theme toggle
- Sidebar navigation
- Main content area
- Search modal

### `css/style.css`
Complete styling with:
- CSS variables for easy theming
- Light and dark mode
- Responsive design breakpoints
- Modern UI components

### `js/app.js`
JavaScript application with:
- Navigation menu building
- Markdown to HTML rendering
- Search functionality
- Theme toggle
- Keyboard shortcuts

## Configuration

### Adding Documentation Files

Edit `js/app.js` and update the `docStructure`:

```javascript
const docStructure = {
    sections: [
        {
            title: 'My Section',
            items: [
                {
                    title: 'My Document',
                    path: '../path/to/document.md',
                    id: 'unique-id'
                }
            ]
        }
    ]
};
```

### Customizing Theme

Edit `css/style.css` and modify CSS variables:

```css
:root {
    --accent-color: #2563eb;
    --bg-primary: #ffffff;
    --text-primary: #1a1a1a;
    /* ... more variables */
}
```

### Changing Header

Edit the header section in `index.html`:

```html
<div class="logo">
    <h1>Your Project Name</h1>
    <p class="subtitle">Your subtitle</p>
</div>
```

## Deployment

See [DEPLOYMENT.md](DEPLOYMENT.md) for complete deployment options:

- **Static Hosting**: GitHub Pages, Netlify, Vercel, Cloudflare Pages
- **Spring Boot**: Serve from your application
- **Docker**: Containerized deployment
- **Custom Servers**: Any HTTP server

## Browser Support

- Chrome/Edge 90+
- Firefox 88+
- Safari 14+
- Mobile browsers (iOS 14+, Android 12+)

## Features

### 🌙 Dark Mode

Automatically switches based on system preference, with manual toggle available. Preference is saved in localStorage.

### 🔍 Search

- Searches document titles and sections
- Builds index from markdown headings
- Real-time results
- Keyboard shortcut: `Cmd/Ctrl + K`

### ⌨️ Keyboard Shortcuts

| Shortcut | Action |
|----------|--------|
| `Cmd/Ctrl + K` | Open search |
| `Escape` | Close modals |
| `←` Arrow Left | Previous page |
| `→` Arrow Right | Next page |

### 📱 Responsive Design

- Desktop: Full sidebar + content
- Tablet: Collapsible sidebar
- Mobile: Hidden sidebar with toggle button

## Dependencies

The documentation site uses CDN-hosted libraries (no npm required):

- **marked.js**: Markdown to HTML conversion
- **highlight.js**: Syntax highlighting for code blocks

All styling is pure CSS with no preprocessors needed.

## Performance

- **Initial Load**: < 1s
- **Search Index Build**: ~500ms
- **Page Navigation**: Instant (cached)
- **Bundle Size**: ~50KB (uncompressed)
- **No Build Step**: Pure HTML/CSS/JS

## Development Tips

### Adding a New Section

1. Edit `js/app.js`
2. Add to `docStructure.sections`
3. Create/reference your markdown file
4. Site updates automatically

### Customizing Styles

All styles are in `css/style.css` with clear sections:
- Variables at top
- Component styles
- Responsive breakpoints at bottom

### Debugging

Open browser DevTools (F12) to:
- Check for JavaScript errors
- View loaded resources
- Debug CSS styles
- Test responsive design

## License

Part of the Spring Boot MCP Companion project - Apache 2.0 License

## Support

For issues or improvements:
1. Check [DEPLOYMENT.md](DEPLOYMENT.md) for setup help
2. Review the configuration options above
3. Check browser console for errors
4. Open an issue on GitHub

---

**Ready to deploy?** → See [DEPLOYMENT.md](DEPLOYMENT.md)
