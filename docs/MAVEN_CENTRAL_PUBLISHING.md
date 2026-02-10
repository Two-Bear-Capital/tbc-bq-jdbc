# Publishing to Maven Central

This document describes how to publish the **tbc-bq-jdbc** driver to Maven Central using the modern Central Portal publishing method.

## Overview

Maven Central publishing has been modernized with the **Central Portal** and simplified plugin. The old OSSRH/Nexus Staging approach is now legacy.

**Key Changes:**
- ✅ Single plugin: `central-publishing-maven-plugin`
- ✅ Automated bundle creation and validation
- ✅ Optional auto-publishing for CI/CD
- ✅ Simpler configuration

**Official Documentation:** https://central.sonatype.org/publish/publish-portal-maven/

## Prerequisites

### 1. Central Portal Account

Register for a Sonatype Central Portal account:

1. Visit [Sonatype Central Portal](https://central.sonatype.com/)
2. Sign up using GitHub, Google, or email
3. Verify your email address

### 2. Namespace Verification

Claim the `vc.tbc` namespace:

**Option A: GitHub Organization Verification (Recommended)**
1. Go to [Namespaces](https://central.sonatype.com/publishing/namespaces) in Central Portal
2. Click **Add Namespace**
3. Enter `vc.tbc` as namespace
4. Select verification method: **GitHub**
5. Follow instructions to add verification repository
6. Wait for automatic verification (typically instant)

**Option B: Domain Verification**
1. Go to [Namespaces](https://central.sonatype.com/publishing/namespaces)
2. Click **Add Namespace**
3. Enter `vc.tbc`
4. Select verification method: **DNS TXT Record**
5. Add the provided TXT record to `tbc.vc` domain DNS
6. Click **Verify** (usually takes a few minutes)

### 3. Generate User Token

Generate a deployment token:

1. Log in to [Central Portal](https://central.sonatype.com/)
2. Click your username → **View Account**
3. Click **Generate User Token**
4. Save both the **username** and **token** (password)
   - Format: `username` (not your email) and random token string
   - You'll need these for GitHub Secrets

### 4. GPG Key for Signing

Maven Central requires all artifacts to be cryptographically signed.

#### Generate GPG Key

```bash
# Generate a new GPG key (4096-bit RSA recommended)
gpg --full-generate-key

# Prompts:
# - Type: (1) RSA and RSA
# - Key size: 4096
# - Expiration: 0 = never, or 2y = 2 years
# - Name: Two Bear Capital
# - Email: (email associated with Central Portal account)
# - Passphrase: (strong passphrase - save securely!)

# Verify key creation
gpg --list-secret-keys --keyid-format=long
```

#### Publish GPG Public Key

```bash
# Get your key ID from the output above
# Example: sec   rsa4096/3AA5C34371567BD2
#                          ^^^^^^^^^^^^^^^^ (your KEY_ID)

# Publish to multiple keyservers
gpg --keyserver keys.openpgp.org --send-keys YOUR_KEY_ID
gpg --keyserver keyserver.ubuntu.com --send-keys YOUR_KEY_ID
```

#### Export GPG Private Key for GitHub Actions

```bash
# Export ASCII-armored private key
gpg --export-secret-keys -a YOUR_KEY_ID > gpg-private.key

# The file should start with:
# -----BEGIN PGP PRIVATE KEY BLOCK-----
```

**IMPORTANT:** Delete this file after adding to GitHub Secrets!

## GitHub Secrets Configuration

Configure these secrets in your repository:

**Settings → Secrets and variables → Actions → New repository secret**

| Secret Name | Description | How to Get |
|------------|-------------|------------|
| `MAVEN_CENTRAL_USERNAME` | Central Portal token username | From "Generate User Token" (NOT your email) |
| `MAVEN_CENTRAL_TOKEN` | Central Portal token password | From "Generate User Token" (random string) |
| `GPG_PRIVATE_KEY` | GPG private key (ASCII-armored) | Contents of `gpg-private.key` file |
| `GPG_PASSPHRASE` | GPG key passphrase | Passphrase from GPG key creation |

## POM Configuration

The POM is configured with the modern `central-publishing-maven-plugin`:

```xml
<plugin>
    <groupId>org.sonatype.central</groupId>
    <artifactId>central-publishing-maven-plugin</artifactId>
    <version>1.0.52</version>
    <extensions>true</extensions>
    <configuration>
        <publishingServerId>central</publishingServerId>
        <autoPublish>true</autoPublish>
        <waitUntil>published</waitUntil>
    </configuration>
</plugin>
```

**Configuration Options:**
- `publishingServerId`: Server ID in settings.xml (uses `central`)
- `autoPublish`: Automatically publish after validation (true for CI/CD)
- `waitUntil`: Wait until artifacts are `published` (or `validated` for manual approval)

## Publishing Process

### Automatic Publishing via GitHub Actions

The easiest way to publish is using the GitHub Actions workflow:

1. Go to **Actions** tab in GitHub repository
2. Select **Publish to Maven Central** workflow
3. Click **Run workflow**
4. Optionally specify version (or leave empty to use POM version)
5. Click **Run workflow** button

The workflow will:
1. ✅ Build and verify the project
2. ✅ Sign all artifacts with GPG
3. ✅ Create deployment bundle
4. ✅ Upload to Central Portal
5. ✅ Automatically validate and publish
6. ✅ Wait until published to Maven Central

**Expected Timeline:**
- Upload and validation: 1-5 minutes
- Publishing to Maven Central: 10-30 minutes
- Maven Central search indexing: 1-2 hours

### Manual Local Publishing

For testing or manual deployment:

#### Configure Maven Settings

Create or edit `~/.m2/settings.xml`:

```xml
<settings>
  <servers>
    <server>
      <id>central</id>
      <username>YOUR_TOKEN_USERNAME</username>
      <password>YOUR_TOKEN_PASSWORD</password>
    </server>
  </servers>
</settings>
```

#### Deploy to Maven Central

```bash
# Ensure GPG key is available
gpg --list-secret-keys

# Build and deploy
./mvnw clean deploy -Prelease

# Or specify GPG key
./mvnw clean deploy -Prelease -Dgpg.keyname=YOUR_KEY_ID
```

The `release` profile activates:
- GPG signing of all artifacts
- Central Publishing plugin with auto-publish

## Artifact Variants

Published artifacts for version `1.0.43`:

| Artifact | Classifier | Size | Description |
|----------|-----------|------|-------------|
| `tbc-bq-jdbc-1.0.52.jar` | *(none)* | ~60KB | Slim JAR (requires dependencies) |
| `tbc-bq-jdbc-1.0.52.jar` | `shaded` | ~38MB | Fat JAR with all dependencies |
| `tbc-bq-jdbc-1.0.52-with-logging.jar` | `with-logging` | ~39MB | Fat JAR + Logback (for IntelliJ) |
| `tbc-bq-jdbc-1.0.52.jar` | `sources` | ~200KB | Source code |
| `tbc-bq-jdbc-1.0.52.jar` | `javadoc` | ~500KB | Javadoc |

Each artifact includes:
- `.md5` - MD5 checksum
- `.sha1` - SHA-1 checksum
- `.sha256` - SHA-256 checksum
- `.sha512` - SHA-512 checksum
- `.asc` - GPG signature

All checksums and signatures are generated automatically by the plugin.

## Required Metadata (POM)

Maven Central requires these fields in your POM:

- ✅ `groupId`: `vc.tbc`
- ✅ `artifactId`: `tbc-bq-jdbc`
- ✅ `version`: `1.0.43` (no SNAPSHOT for releases)
- ✅ `name`: BigQuery JDBC Driver
- ✅ `description`: Modern JDBC driver for Google BigQuery, built for Java 21+
- ✅ `url`: https://github.com/Two-Bear-Capital/tbc-bq-jdbc
- ✅ `licenses`: Apache License 2.0
- ✅ `developers`: Developer information
- ✅ `scm`: Source control management URLs
- ✅ Source JAR (`-sources.jar`)
- ✅ Javadoc JAR (`-javadoc.jar`)
- ✅ GPG signatures (`.asc` files)

All requirements are already configured in the POM.

## Deployment Timeline

1. **Build & Sign** (local or CI): 1-2 minutes
2. **Upload to Central Portal**: 30 seconds - 2 minutes
3. **Validation**: 1-3 minutes (automatic)
4. **Publishing** (if autoPublish=true): 5-15 minutes
5. **Maven Central Sync**: 10-30 minutes after publishing
6. **Search Index Update**: 1-2 hours

**Total time to availability:** ~15-45 minutes
**Total time to search visibility:** 1-2 hours

## Verification

### Check Deployment Status

1. **Central Portal Deployments:**
   - https://central.sonatype.com/publishing/deployments
   - View validation status and publishing progress

2. **Central Portal Artifact Page:**
   - https://central.sonatype.com/artifact/vc.tbc/tbc-bq-jdbc
   - Shows published versions and download stats

3. **Maven Central Search:**
   - https://search.maven.org/artifact/vc.tbc/tbc-bq-jdbc
   - Available after search index updates (~2 hours)

4. **Direct Maven Central URL:**
   - https://repo1.maven.org/maven2/vc/tbc/tbc-bq-jdbc/
   - Browse directory of published artifacts

### Test Installation

After publishing, test that users can download:

```bash
# Create test POM
cat > pom.xml << 'EOF'
<project>
  <modelVersion>4.0.0</modelVersion>
  <groupId>test</groupId>
  <artifactId>test</artifactId>
  <version>1.0</version>
  <dependencies>
    <dependency>
      <groupId>vc.tbc</groupId>
      <artifactId>tbc-bq-jdbc</artifactId>
      <version>1.0.52</version>
    </dependency>
  </dependencies>
</project>
EOF

# Test download
mvn dependency:resolve
```

## Usage After Publishing

Once published to Maven Central, users can add the driver as a dependency:

### Maven

```xml
<dependency>
    <groupId>vc.tbc</groupId>
    <artifactId>tbc-bq-jdbc</artifactId>
    <version>1.0.52</version>
</dependency>

<!-- Or use shaded JAR (includes all dependencies) -->
<dependency>
    <groupId>vc.tbc</groupId>
    <artifactId>tbc-bq-jdbc</artifactId>
    <version>1.0.52</version>
    <classifier>shaded</classifier>
</dependency>
```

### Gradle

```groovy
dependencies {
    implementation 'vc.tbc:tbc-bq-jdbc:1.0.52'

    // Or shaded variant
    implementation 'vc.tbc:tbc-bq-jdbc:1.0.52:shaded'
}
```

### Direct JAR Download

```bash
# Download from Maven Central
wget https://repo1.maven.org/maven2/vc/tbc/tbc-bq-jdbc/1.0.52/tbc-bq-jdbc-1.0.52.jar
```

## Troubleshooting

### Common Issues

#### 1. "401 Unauthorized" Error

**Cause:** Invalid or expired token credentials

**Solution:**
```bash
# Regenerate token at https://central.sonatype.com/account
# Update MAVEN_CENTRAL_USERNAME and MAVEN_CENTRAL_TOKEN secrets
```

#### 2. "Invalid Signature" or GPG Errors

**Cause:** GPG key not found or passphrase incorrect

**Solution:**
```bash
# Verify GPG key exists
gpg --list-secret-keys

# Test signing
echo "test" | gpg --clearsign

# Verify GitHub Secrets:
# - GPG_PRIVATE_KEY contains full ASCII-armored key
# - GPG_PASSPHRASE is correct
```

#### 3. "Namespace not verified" Error

**Cause:** Namespace `vc.tbc` not claimed or verified

**Solution:**
- Visit https://central.sonatype.com/publishing/namespaces
- Verify namespace claim is approved
- Check verification status (GitHub or DNS)

#### 4. "Validation Failed" Error

**Cause:** Missing required POM elements or artifacts

**Solution:**
- Check Central Portal deployment details for specific errors
- Common issues:
  - Missing `-sources.jar` or `-javadoc.jar`
  - Missing GPG signatures
  - Invalid POM metadata (license, developers, scm)

#### 5. Artifacts Not Appearing in Search

**Cause:** Maven Central search index not yet updated

**Solution:**
- Wait up to 2 hours for search indexing
- Artifacts are available for download immediately after publishing
- Use direct URL: https://repo1.maven.org/maven2/vc/tbc/tbc-bq-jdbc/

## Development Workflow

### Standard Release Process

1. **Develop and test locally**
   ```bash
   git checkout develop
   # ... make changes ...
   ./mvnw clean verify
   git commit -m "Add feature"
   git push origin develop
   ```

2. **Merge to main (triggers GitHub Packages publish)**
   ```bash
   gh pr create --base main --head develop
   gh pr merge --auto
   # Automatic version bump triggered by CI
   ```

3. **Publish to Maven Central**
   - Go to **Actions → Publish to Maven Central**
   - Click **Run workflow**
   - Artifacts published to both GitHub Packages and Maven Central

### Dual Publishing Strategy

The project publishes to **two repositories**:

| Repository | Trigger | Timeline | Audience |
|-----------|---------|----------|----------|
| **GitHub Packages** | Automatic on push to `main` | Immediate | Internal, CI/CD |
| **Maven Central** | Manual GitHub Action | 15-45 min | Public, production |

This provides:
- ✅ Fast internal artifact availability
- ✅ Stable public releases
- ✅ Redundancy and backup

## Security Best Practices

1. **Never commit secrets** to repository
2. **Use GitHub Secrets** for all credentials
3. **Rotate tokens annually** or when compromised
4. **Use strong GPG passphrases** (20+ characters)
5. **Limit repository access** to trusted maintainers
6. **Enable branch protection** on `main` branch
7. **Review deployment logs** for anomalies

## Cleanup After Setup

After configuring GitHub Secrets:

```bash
# Securely delete exported GPG key
shred -u gpg-private.key  # Linux
srm gpg-private.key        # macOS with srm
rm -P gpg-private.key      # macOS native

# Your GPG key remains in local keyring
gpg --list-secret-keys
```

## Support and Resources

### Official Documentation
- [Maven Central Publishing](https://central.sonatype.org/publish/)
- [Central Portal Guide](https://central.sonatype.org/publish/publish-portal-maven/)
- [Requirements](https://central.sonatype.org/publish/requirements/)
- [GPG Signing](https://central.sonatype.org/publish/requirements/gpg/)

### Help and Support
- **Central Portal Issues:** https://central.sonatype.org/support/
- **Plugin Issues:** https://github.com/sonatype/central-publishing-maven-plugin
- **Driver Issues:** https://github.com/Two-Bear-Capital/tbc-bq-jdbc/issues
- **Status Page:** https://status.maven.org/

## Appendix: Plugin Configuration Options

```xml
<configuration>
    <!-- Required: Server ID in settings.xml -->
    <publishingServerId>central</publishingServerId>

    <!-- Auto-publish after validation (true for CI/CD) -->
    <autoPublish>true</autoPublish>

    <!-- Wait until validation or publishing completes -->
    <waitUntil>published</waitUntil>  <!-- or 'validated' -->

    <!-- Maximum wait time (default: 60 minutes) -->
    <waitMaxTime>60</waitMaxTime>

    <!-- Retry configuration -->
    <retryAttempts>3</retryAttempts>
</configuration>
```

**Recommended Settings:**
- **CI/CD:** `autoPublish=true`, `waitUntil=published`
- **Manual Approval:** `autoPublish=false`, `waitUntil=validated`
- **Quick Deploy:** `waitUntil=validated` (don't wait for publishing)
