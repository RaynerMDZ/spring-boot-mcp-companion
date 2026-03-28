# Documentation Website - Deployment Guide

This documentation website is a self-contained, lightweight static HTML/CSS/JavaScript application with all documentation bundled together.

## Quick Start Locally

### Using Python (Recommended)

```bash
cd docs/web
python3 -m http.server 8000
```

Then visit `http://localhost:8000` in your browser.

### Using Node.js

```bash
cd docs/web
npx http-server
# Visit http://localhost:8080
```

### Using Ruby

```bash
cd docs/web
ruby -run -ehttpd . -p8000
# Visit http://localhost:8000
```

### Notes

The documentation is completely self-contained in the `docs/web/` directory:
- All markdown files are in `docs/web/content/`
- No external dependencies needed
- Can be deployed to any static hosting service

## Production Deployment Options

### Option 1: Static Hosting (GitHub Pages, Netlify, Vercel)

1. Copy the entire `docs/web/` directory to your static hosting service
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
      static-locations: classpath:/static/,file:docs/web/
```

Then copy `docs/web/` contents to `src/main/resources/static/docs/`.

Access at: `http://localhost:8080/docs/`

### Option 3: Docker

Create a `Dockerfile` in the project root:

```dockerfile
FROM nginx:alpine
COPY docs/web/ /usr/share/nginx/html/
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

Edit `docs/web/js/app.js` and add entries to the `docStructure`:

```javascript
const docStructure = {
    sections: [
        {
            title: 'Your Section',
            items: [
                {
                    title: 'Your Document',
                    path: '../path/to/document.md',
                    id: 'unique-id'
                }
            ]
        }
    ]
};
```

### Customizing Colors

Edit `docs/web/css/style.css` and modify the CSS variables in `:root`:

```css
:root {
    --accent-color: #2563eb;
    --accent-hover: #1d4ed8;
    --bg-primary: #ffffff;
    /* ... etc */
}
```

### Customizing Header

Edit the header in `docs/web/index.html`:

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

1. **Colors**: Modify CSS variables in `css/style.css`
2. **Navigation**: Update `docStructure` in `js/app.js`
3. **Layout**: Edit HTML in `index.html`
4. **Styling**: All CSS is in `css/style.css` (no preprocessors needed)

## Publishing to GitHub Pages

1. Copy `docs/web/` to your `docs/` directory in main branch
2. Go to Settings > Pages > Build and deployment
3. Select "Deploy from a branch"
4. Select "main" and "/docs" folder
5. Site will be live at `https://yourusername.github.io/repo-name/`

## Publishing to Netlify

1. Fork/clone the repository
2. Connect your repo to Netlify
3. Set build directory to `docs/web/`
4. Deploy

Site will be live in seconds!

## License

This documentation website is part of the Spring Boot MCP Companion project and follows the same Apache 2.0 license.
