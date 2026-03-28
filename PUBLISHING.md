# Publishing to Maven Central

Guide for publishing Spring Boot MCP Companion to Maven Central Repository.

## Prerequisites

### 1. Sonatype OSSRH Account

Create an account at [Sonatype JIRA](https://issues.sonatype.org/):
- Sign up for free account
- Create a ticket to claim your namespace `com.raynermendez`
- Sonatype will verify your domain/GitHub ownership
- Once approved, you'll have access to OSSRH

### 2. GPG Key for Code Signing

Required to sign artifacts before publishing:

```bash
# Install GPG (if not already installed)
# macOS: brew install gnupg
# Linux: sudo apt-get install gnupg
# Windows: Download from https://gpg4win.org/

# Generate GPG key
gpg --full-generate-key

# Follow prompts:
# - Key type: RSA
# - Key size: 4096
# - Real name: Your Name
# - Email: your-email@example.com
# - Passphrase: REMEMBER THIS! You'll need it later
```

```bash
# List your keys to get the key ID
gpg --list-secret-keys --keyid-format SHORT

# Expected output:
# sec   rsa4096/XXXXXXXX 2026-03-27 [SC]
#       ^^^^^^^^^^ <- KEY_ID

# Export and publish your key (replace XXXXXXXX with your KEY_ID)
gpg --keyserver hkp://keyserver.ubuntu.com --send-keys XXXXXXXX

# Verify it was published
gpg --keyserver hkp://keyserver.ubuntu.com --search-keys your-email@example.com
```

### 3. Maven Settings Configuration

Update `~/.m2/settings.xml` (create if doesn't exist):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd">

  <servers>
    <!-- Sonatype OSSRH credentials -->
    <server>
      <id>ossrh</id>
      <username>YOUR_SONATYPE_USERNAME</username>
      <password>YOUR_SONATYPE_PASSWORD</password>
    </server>

    <!-- GPG passphrase (or use -Dgpg.passphrase at command line) -->
    <!-- Optional: store in environment variable instead -->
  </servers>

  <profiles>
    <profile>
      <id>ossrh</id>
      <activation>
        <activeByDefault>true</activeByDefault>
      </activation>
      <properties>
        <!-- Your GPG key ID -->
        <gpg.keyname>XXXXXXXX</gpg.keyname>
        <!-- Optional: store passphrase securely -->
        <!-- <gpg.passphrase>YOUR_GPG_PASSPHRASE</gpg.passphrase> -->
      </properties>
    </profile>
  </profiles>
</settings>
```

**Note:** For security:
- Store Sonatype credentials in `~/.m2/settings-security.xml` (encrypted)
- Or use environment variables: `OSSRH_USERNAME`, `OSSRH_PASSWORD`
- Pass GPG passphrase at command line: `-Dgpg.passphrase=PASSPHRASE`

## Publishing Steps

### Step 1: Update pom.xml

Ensure `pom.xml` has:
- Complete metadata (name, description, URL, licenses)
- Proper SCM configuration
- Developer information
- Distribution management
- Required plugins (gpg, javadoc, source, nexus-staging)

Check that your version ends in `-SNAPSHOT` (e.g., `1.0.0-SNAPSHOT`)

```bash
# View current version
grep "<version>" pom.xml | head -1
```

### Step 2: Prepare Release

```bash
# Ensure everything is committed
git status

# Clean build
mvn clean verify

# Run all tests
mvn clean test
```

### Step 3: Build and Sign Artifacts

```bash
# Build with GPG signing (will prompt for passphrase)
mvn clean package gpg:sign

# Or pass passphrase directly
mvn clean package gpg:sign -Dgpg.passphrase=YOUR_PASSPHRASE
```

### Step 4: Deploy to OSSRH Staging

```bash
# Deploy to Sonatype OSSRH staging repository
mvn clean deploy -Dgpg.passphrase=YOUR_PASSPHRASE

# Or with environment variables
OSSRH_USERNAME=your-username OSSRH_PASSWORD=your-password \
  mvn clean deploy -Dgpg.passphrase=YOUR_PASSPHRASE
```

Expected output:
```
[INFO] Uploading to ossrh: https://oss.sonatype.org/service/local/staging/deployByRepositoryId/...
[INFO] BUILD SUCCESS
```

### Step 5: Release from Staging to Central

Two options:

**Option A: Manual Release (via Sonatype UI)**

1. Go to [Sonatype Nexus](https://oss.sonatype.org/)
2. Login with your credentials
3. Click "Staging Repositories"
4. Find your repository (search by `raynermendez`)
5. Click "Close" to validate artifacts
6. Wait for validation to pass
7. Click "Release" to publish to Central

**Option B: Automated Release (Maven Plugin)**

```bash
# Close and release in one command
mvn nexus-staging:release -Ddescription="Release 1.0.0"

# Or step by step
mvn nexus-staging:close -Ddescription="Release 1.0.0"
mvn nexus-staging:release
```

### Step 6: Verify Publication

Wait 2-4 hours for synchronization to Maven Central:

```bash
# Check Maven Central
curl https://repo1.maven.org/maven2/com/raynermendez/spring-boot-mcp-companion/

# Or search
https://search.maven.org/artifact/com.raynermendez/spring-boot-mcp-companion
```

## Version Management

### Snapshots (Development)
- Version: `X.Y.Z-SNAPSHOT` (e.g., `1.0.0-SNAPSHOT`)
- Repository: OSSRH Snapshots
- Always published, can be overwritten
- Users can depend on latest snapshot

### Releases (Stable)
- Version: `X.Y.Z` (e.g., `1.0.0`)
- Repository: Maven Central (after 2-4 hour sync)
- Immutable once published
- Recommended for production use

### Release Workflow

```bash
# 1. Update version in pom.xml (1.0.0-SNAPSHOT → 1.0.0)
mvn versions:set -DnewVersion=1.0.0

# 2. Commit
git add pom.xml
git commit -m "Release version 1.0.0"
git tag -a v1.0.0 -m "Release v1.0.0"

# 3. Deploy (see Step 4 above)
mvn clean deploy -Dgpg.passphrase=YOUR_PASSPHRASE

# 4. Release from staging
mvn nexus-staging:release -Ddescription="Release 1.0.0"

# 5. Push tag
git push origin v1.0.0

# 6. Update version to next snapshot (1.0.1-SNAPSHOT)
mvn versions:set -DnewVersion=1.0.1-SNAPSHOT
git add pom.xml
git commit -m "Prepare next development version"
git push origin main
```

## Troubleshooting

### GPG Key Not Found
```bash
# List available keys
gpg --list-secret-keys

# Use key name instead of ID
mvn clean deploy -Dgpg.keyname=your-email@example.com
```

### Invalid Signature
```bash
# Ensure GPG is properly configured
gpg --version

# Test signing
echo "test" | gpg -s
```

### Sonatype Credentials Invalid
```bash
# Verify credentials
curl -u YOUR_USERNAME:YOUR_PASSWORD https://oss.sonatype.org/service/local/status

# Should return XML with status information
```

### Staging Repository Not Found
- Wait a few seconds after deployment
- Check repository list on Nexus UI
- Verify artifact was actually uploaded (check logs)

### Validation Failed
- Check artifact contents: `jar -tf target/*.jar`
- Ensure Javadoc JAR exists
- Ensure source JAR exists
- Check pom.xml metadata is complete

## Security Best Practices

1. **Never commit credentials to Git**
   - Use `~/.m2/settings.xml` only
   - Use environment variables
   - Use encrypted settings in Maven

2. **Protect your GPG key**
   - Use strong passphrase
   - Don't share key file
   - Consider using hardware security key

3. **Use environment variables for CI/CD**
   ```bash
   export OSSRH_USERNAME=your-username
   export OSSRH_PASSWORD=your-password
   export GPG_PASSPHRASE=your-passphrase

   mvn clean deploy
   ```

4. **GitHub Actions Example**
   ```yaml
   - name: Deploy to Maven Central
     env:
       OSSRH_USERNAME: ${{ secrets.OSSRH_USERNAME }}
       OSSRH_PASSWORD: ${{ secrets.OSSRH_PASSWORD }}
       GPG_PASSPHRASE: ${{ secrets.GPG_PASSPHRASE }}
     run: mvn clean deploy
   ```

## Reference Links

- [Sonatype OSSRH Guide](https://central.sonatype.org/publish/publish-guide/)
- [Maven GPG Plugin](https://maven.apache.org/plugins/maven-gpg-plugin/)
- [Nexus Staging Plugin](https://github.com/sonatype/nexus-maven-plugins/tree/master/staging)
- [Search Maven Central](https://search.maven.org/)
- [Sonatype Jira](https://issues.sonatype.org/)

## Common Issues & Solutions

| Issue | Solution |
|-------|----------|
| "403 Forbidden" from OSSRH | Check credentials in settings.xml |
| "Invalid POM" error | Complete all metadata: name, description, url, licenses, developers, scm |
| "Missing source/Javadoc JAR" | Ensure maven-source-plugin and maven-javadoc-plugin are in pom.xml |
| "GPG key not found" | Export your public key to keyserver first |
| "Can't close repository" | Check validation errors in Nexus UI |
| Artifacts not in Central after 4 hours | Check Nexus UI; may need manual promotion |

---

**Ready to publish?** Follow the steps above. For more help, see [Sonatype's official guide](https://central.sonatype.org/publish/publish-guide/).
