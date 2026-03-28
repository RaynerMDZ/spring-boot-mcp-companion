# Contributing to Spring Boot MCP Companion

Thank you for your interest in contributing to Spring Boot MCP Companion! We welcome contributions from everyone.

## Code of Conduct

We are committed to providing a welcoming and inspiring community for all. Please be respectful and constructive in all interactions.

## How to Contribute

### 1. Report Bugs

If you find a bug, please [open an issue](https://github.com/yourusername/spring-boot-mcp-companion/issues) with:

- **Clear description** of the bug
- **Steps to reproduce** the issue
- **Expected behavior** vs actual behavior
- **Environment** (OS, Java version, Spring Boot version)
- **Minimal code example** if applicable

```
Title: [BUG] Tools not registered when using @EnableMcpCompanion

Description:
When I add @EnableMcpCompanion to my application, no tools are being registered.

Steps:
1. Create Spring Boot app
2. Add @EnableMcpCompanion to main class
3. Create @Service with @McpTool method
4. Start application
5. Call POST /mcp/tools/list

Expected: See tool in response
Actual: Empty tools array returned

Environment:
- OS: macOS 14.2
- Java: 25.0.2
- Spring Boot: 4.0.5
```

### 2. Request Features

Have an idea? [Create a feature request](https://github.com/yourusername/spring-boot-mcp-companion/issues) with:

- **Clear use case** for the feature
- **Proposed API/design** (code examples welcome)
- **Benefit** to users
- **Alternatives** you've considered

```
Title: [FEATURE] Streaming responses for long-running tools

Use Case:
Some tools take a long time (30+ seconds). Would be great to stream
partial results back to the client instead of waiting for completion.

Proposed API:
@McpTool(streaming = true)
public Stream<String> longRunningTool(...) {
    return results.stream();
}

Benefits:
- Better UX for long-running operations
- Can start processing partial results
- Reduced timeout issues

Alternatives:
- Polling endpoint (inefficient)
- Batch jobs (requires persistence)
```

### 3. Improve Documentation

Documentation improvements are always welcome:

- Fix typos or unclear explanations in README
- Add more examples for specific use cases
- Improve API reference
- Add troubleshooting guides
- Suggest architectural improvements

Simply fork the repository and submit a pull request with your changes.

### 4. Submit Code Changes

We follow a standard Git workflow:

#### Step 1: Fork and Clone

```bash
# Fork the repository on GitHub
# Clone your fork
git clone https://github.com/yourusername/spring-boot-mcp-companion.git
cd spring-boot-mcp-companion

# Add upstream remote
git remote add upstream https://github.com/raynermendez/spring-boot-mcp-companion.git
```

#### Step 2: Create Feature Branch

```bash
# Update main from upstream
git fetch upstream
git checkout main
git merge upstream/main

# Create feature branch
git checkout -b feature/amazing-feature
# or
git checkout -b fix/issue-123
# or
git checkout -b docs/improve-guide
```

#### Step 3: Make Changes

Follow these guidelines:

**Code Style:**
- Use [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
- 4-space indentation
- 100-character line limit (soft)
- Meaningful variable names
- English comments

**Format:**
```bash
# Auto-format code
mvn spotless:apply
```

**Example:**
```java
/**
 * Retrieves an order by its unique ID.
 *
 * Performs database lookup with automatic caching.
 *
 * @param orderId the order ID to retrieve
 * @return the Order object
 * @throws OrderNotFoundException if order not found
 */
@McpTool(description = "Get order by ID")
public Order getOrder(@McpInput(required = true) String orderId) {
    return orderRepository.findById(orderId)
        .orElseThrow(() -> new OrderNotFoundException(orderId));
}
```

#### Step 4: Write Tests

All code changes must include tests:

```bash
# Add tests for your changes
vim src/test/java/com/raynermendez/spring_boot_mcp_companion/YourFeatureTest.java

# Run tests
mvn clean test

# Run specific test
mvn test -Dtest=YourFeatureTest

# Check coverage
mvn clean verify jacoco:report
```

**Test Example:**
```java
@SpringBootTest(classes = TestConfig.class)
class YourFeatureTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void shouldRegisterToolWhenAnnotationPresent() {
        assertTrue(context.containsBean("yourToolName"));
    }

    @Test
    void shouldValidateInputParameters() {
        // Test validation
    }

    @Test
    void shouldHandleErrorsGracefully() {
        // Test error cases
    }
}
```

**Testing Guidelines:**
- Test happy path, edge cases, and errors
- Use descriptive test names (should...)
- Use @DisplayName for complex scenarios
- Mock external dependencies
- Use @SpringBootTest for integration tests
- Aim for 80%+ code coverage

#### Step 5: Commit Changes

```bash
# Stage your changes
git add src/main/java/...
git add src/test/java/...

# Commit with clear message
git commit -m "Add feature: streaming responses for tools"

# Good commit messages:
# - Imperative mood: "Add feature" not "Added feature"
# - One sentence summary (50 chars max)
# - Blank line + detailed explanation (if needed)
# - Reference issues: "Fixes #123"

# Example:
git commit -m "Add streaming responses for long-running tools

Previously, tools had to return all results at once, causing
timeouts for operations > 30 seconds.

Now tools can be marked with streaming=true to return results
incrementally via stream. Client receives partial results
instead of waiting for completion.

Fixes #456"
```

**Commit Message Guidelines:**
- Use imperative mood ("Add", "Fix", "Update")
- Reference issue numbers (#123)
- Keep first line under 50 characters
- Wrap body at 72 characters
- Explain WHY, not WHAT (code shows what)

#### Step 6: Push and Create Pull Request

```bash
# Push to your fork
git push origin feature/amazing-feature

# Go to GitHub and create pull request
# Write a clear PR description:
```

**Pull Request Template:**
```markdown
## Description
Brief description of changes.

## Type of Change
- [ ] Bug fix
- [ ] New feature
- [ ] Documentation update
- [ ] Performance improvement
- [ ] Refactoring

## Related Issues
Fixes #123, Related to #456

## Changes Made
- Change 1
- Change 2
- Change 3

## Testing
- [ ] Added/updated unit tests
- [ ] Added/updated integration tests
- [ ] Tested manually (describe)
- [ ] All tests passing

## Checklist
- [ ] Code follows style guidelines
- [ ] No breaking changes
- [ ] Documentation updated
- [ ] Changelog updated (if applicable)
- [ ] Commits are clean
```

#### Step 7: Respond to Review

Maintainers will review your PR:

```bash
# If changes requested:
git add src/...
git commit -m "Review feedback: address naming concerns"
git push origin feature/amazing-feature
# (No need to create new PR, it updates automatically)
```

## Development Setup

### Prerequisites
- Java 25+
- Maven 3.8+
- Git

### Quick Start

```bash
# Clone repository
git clone https://github.com/yourusername/spring-boot-mcp-companion.git
cd spring-boot-mcp-companion

# Build project
mvn clean install

# Run tests
mvn clean verify

# Start sample application
mvn spring-boot:run

# Access API
curl http://localhost:8080/mcp/server-info
```

### IDE Setup

**IntelliJ IDEA:**
1. Open project
2. Right-click `pom.xml` → Add as Maven Project
3. Configure SDK: Settings → Project → SDK (Java 25)
4. Enable annotation processing: Settings → Build → Annotation Processors

**Eclipse:**
1. Import as Maven project
2. Right-click project → Maven → Update Project
3. Configure Java 25 compiler

**Visual Studio Code:**
- Install "Extension Pack for Java"
- Install "Maven for Java"

### Common Development Tasks

```bash
# Format code
mvn spotless:apply

# Run all tests
mvn clean verify

# Run specific test
mvn test -Dtest=EnableMcpCompanionTest#testEnableMcpCompanionAnnotationEnablesAutoConfiguration

# Check test coverage
mvn jacoco:report
open target/site/jacoco/index.html

# Build JAR
mvn clean package

# Run locally
cd target/classes
java -cp ... com.raynermendez.spring_boot_mcp_companion.SpringBootMcpCompanionApplication

# Generate Javadoc
mvn javadoc:javadoc
open target/site/apidocs/index.html
```

## Review Process

### What We Look For

✅ **Good**
- Clear, descriptive commit messages
- Tests for all changes
- Follows code style guide
- Updates documentation
- Handles edge cases
- Solves the problem completely
- Minimal, focused changes

❌ **Needs Work**
- Unclear commit messages
- Missing tests
- Code style violations
- No documentation
- Ignores edge cases
- Partial/incomplete solution
- Unrelated changes bundled together

### Timeline

- **Acknowledgment**: 24 hours
- **Initial review**: 2-7 days
- **Feedback rounds**: ~3 days each
- **Merge**: After approval and CI passing

## Code Review Guidelines

When reviewing others' PRs:

1. **Be respectful** - Assume good intentions
2. **Be specific** - Point to exact lines
3. **Suggest improvements** - "Have you considered...?"
4. **Approve clearly** - Use GitHub "Approve" button
5. **Request changes only if needed** - Don't bikeshed

## Release Process

Only maintainers can release, but here's how it works:

```bash
# Update version in pom.xml
mvn versions:set -DnewVersion=1.1.0

# Update CHANGELOG.md
# Commit: "Release v1.1.0"
# Tag: git tag -a v1.1.0 -m "Release v1.1.0"
# Deploy: mvn clean deploy -P release
```

## Licensing

By contributing, you agree that your contributions will be licensed under the Apache License 2.0.

## Questions?

- [GitHub Discussions](https://github.com/yourusername/spring-boot-mcp-companion/discussions)
- Email: support@example.com

---

Thank you for contributing! 🎉
