# Documentation Website - Deployment Guide

This documentation website is a self-contained, lightweight static HTML/CSS/JavaScript application with all documentation bundled together in `/docs/`.

## Quick Start Locally

### Using Python (Recommended)

```bash
cd docs
python3 -m http.server 8000
```

Then visit `http://localhost:8000` in your browser.

### Using Node.js

```bash
cd docs
npx http-server
# Visit http://localhost:8080
```

### Using Ruby

```bash
cd docs
ruby -run -ehttpd . -p8000
# Visit http://localhost:8000
```

### Notes

The documentation is completely self-contained in the `docs/` directory:
- All markdown files are in `docs/content/`
- HTML, CSS, and JS files at root level
- No external dependencies needed
- Can be deployed to any static hosting service

## Production Deployment Options

### Option 1: Static Hosting (GitHub Pages, Netlify, Vercel)

1. Copy the entire `docs/` directory to your static hosting service
2. No build step required - it's pure HTML/CSS/JS
3. Recommended hosts:
   - **GitHub Pages**: Free, integrates with your repo
   - **Netlify**: Free tier, automatic deployments
   - **Vercel**: Free tier, excellent performance
   - **Cloudflare Pages**: Free tier, fast CDN

### Option 2: Serve with Your Spring Boot Application

Add the following to your Spring Boot `application.yml`:

```yaml
spring:
  web:
    resources:
      static-locations: classpath:/static/,file:docs/
```

Then copy `docs/` contents to `src/main/resources/static/`.

Access at: `http://localhost:8080/`

### Option 3: Docker

Create a `Dockerfile` in the project root:

```dockerfile
FROM nginx:alpine
COPY docs/ /usr/share/nginx/html/
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

Build and run:

```bash
docker build -t spring-boot-mcp-docs .
docker run -p 80:8080 spring-boot-mcp-docs
```

### Option 4: Docker with Spring Boot

Update your `Dockerfile`:

```dockerfile
FROM openjdk:17-jdk-slim
COPY . /app
WORKDIR /app
RUN mvn clean package -DskipTests
EXPOSE 8080
CMD ["java", "-jar", "target/spring-boot-mcp-companion-*.jar"]
```

Then configure Spring Boot to serve the docs (Option 2).

## Features

### ✨ Built-in Capabilities

- **Dark/Light Mode**: Toggle between themes, persisted in localStorage
- **Search**: Full-text search across all documentation (Cmd/Ctrl+K)
- **Navigation**: Previous/Next buttons + keyboard shortcuts
- **Mobile Responsive**: Works on all device sizes
- **Code Highlighting**: Syntax highlighting for code blocks
- **Breadcrumb Navigation**: Easy navigation context
- **Lazy Loading**: Documentation loaded on-demand

### 🎮 Keyboard Shortcuts

- **Cmd/Ctrl + K**: Open search
- **Escape**: Close modals
- **Arrow Left**: Previous page
- **Arrow Right**: Next page

## Configuration

### Adding Documentation

Edit `docs/js/app.js` and add entries to the `docStructure`:

```javascript
const docStructure = {
    sections: [
        {
            title: 'Your Section',
            items: [
                {
                    title: 'Your Document',
                    id: 'unique-id',
                    file: 'content/path/to/document.md'
                }
            ]
        }
    ]
};
```

### Customizing Colors

Edit `docs/css/style.css` and modify the CSS variables in `:root`:

```css
:root {
    --color-primary: #bd93f9;
    --color-accent: #ff79c6;
    --bg-primary: #ffffff;
    /* ... etc */
}
```

### Customizing Header

Edit the header in `docs/index.html`:

```html
<div class="logo">
    <h1>Your Project Name</h1>
    <p class="subtitle">Your subtitle</p>
</div>
```

## Performance

- **Load Time**: < 1s for initial load (depends on doc size)
- **Search Indexing**: ~500ms for full documentation set
- **Bundle Size**: ~50KB total (uncompressed)
- **CDN Dependencies**:
  - Highlight.js (syntax highlighting)
  - Marked.js (markdown rendering)
  - No heavy frameworks

## Browser Support

- Chrome/Edge 90+
- Firefox 88+
- Safari 14+
- Mobile browsers (iOS 14+, Android 12+)

## Troubleshooting

### Documentation Not Loading

1. Verify markdown files exist at the paths specified in `app.js`
2. Check browser console for CORS errors
3. Ensure you're serving from the correct directory

### Search Not Working

1. The search index builds automatically
2. Ensure markdown files are readable
3. Check browser console for errors

### Styling Issues

1. Clear browser cache (Ctrl+Shift+Delete)
2. Verify CSS file is loaded in Network tab
3. Check for CSS variable overrides

## Development

To make the site your own:

1. **Colors**: Modify CSS variables in `docs/css/style.css`
2. **Navigation**: Update `docStructure` in `docs/js/app.js`
3. **Layout**: Edit HTML in `docs/index.html`
4. **Styling**: All CSS is in `docs/css/style.css` (no preprocessors needed)

## Publishing to GitHub Pages

1. Go to Settings > Pages > Build and deployment
2. Select "Deploy from a branch"
3. Select "main" and "/docs" folder
4. Site will be live at `https://yourusername.github.io/repo-name/`

## Publishing to Netlify

1. Fork/clone the repository
2. Connect your repo to Netlify
3. Set build directory to `docs/`
4. Deploy

Site will be live in seconds!

## License

This documentation website is part of the Spring Boot MCP Companion project and follows the same Apache 2.0 license.
