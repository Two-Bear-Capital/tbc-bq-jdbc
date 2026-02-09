# Publishing to Maven Central

This document describes how to publish the **tbc-bq-jdbc** driver to Maven Central.

## Prerequisites

### 1. Sonatype OSSRH Account

Register for a Sonatype account and create a namespace claim:

1. Visit [Sonatype Central Portal](https://central.sonatype.com/)
2. Sign up for an account
3. Create a namespace for `vc.tbc`
   - Option A: Verify GitHub ownership (recommended)
   - Option B: Verify domain ownership for `tbc.com`
4. Wait for namespace approval (usually within 1-2 business days)

**Documentation:** https://central.sonatype.org/register/central-portal/

### 2. GPG Key for Signing

Maven Central requires all artifacts to be cryptographically signed with GPG.

#### Generate GPG Key

```bash
# Generate a new GPG key (use RSA 4096-bit)
gpg --full-generate-key

# Follow prompts:
# - Key type: (1) RSA and RSA
# - Key size: 4096
# - Expiration: 0 (never expires) or set expiration
# - Real name: Two Bear Capital
# - Email: use email associated with Sonatype account
# - Passphrase: strong passphrase (save this!)

# List keys to verify
gpg --list-secret-keys --keyid-format=long
```

#### Publish GPG Public Key

```bash
# Get your key ID from the output above (e.g., 3AA5C34371567BD2)
gpg --keyserver keys.openpgp.org --send-keys YOUR_KEY_ID

# Also send to other keyservers for redundancy
gpg --keyserver keyserver.ubuntu.com --send-keys YOUR_KEY_ID
```

#### Export GPG Private Key

```bash
# Export private key (needed for GitHub Actions)
gpg --export-secret-keys -a YOUR_KEY_ID > gpg-private.key

# The exported key should start with:
# -----BEGIN PGP PRIVATE KEY BLOCK-----
```

**IMPORTANT:** Keep the `gpg-private.key` file secure and delete it after adding to GitHub Secrets!

### 3. GitHub Secrets Configuration

Add these secrets to your GitHub repository:

Go to: **Settings** → **Secrets and variables** → **Actions** → **New repository secret**

| Secret Name | Description | How to Get |
|------------|-------------|------------|
| `OSSRH_USERNAME` | Sonatype account username | From Sonatype Central Portal account |
| `OSSRH_TOKEN` | Sonatype account token | Generate at https://central.sonatype.com/account → Generate User Token |
| `GPG_PRIVATE_KEY` | GPG private key (ASCII-armored) | Contents of `gpg-private.key` file |
| `GPG_PASSPHRASE` | GPG key passphrase | Passphrase used when creating GPG key |

**Security Note:** These secrets are never exposed in logs and are only accessible to GitHub Actions.

## Publishing Process

### Automatic Publishing (Recommended)

The project is configured to publish to **both** GitHub Packages and Maven Central.

#### GitHub Packages (Automatic)
- Triggered on every successful build on `main` branch
- Workflow: `.github/workflows/version-and-release.yml`
- No manual intervention required

#### Maven Central (Manual Trigger)
- Triggered manually via GitHub Actions
- Workflow: `.github/workflows/publish-maven-central.yml`

**To publish to Maven Central:**

1. Go to **Actions** tab in GitHub
2. Select **Publish to Maven Central** workflow
3. Click **Run workflow**
4. Optionally specify a version (or leave empty to use POM version)
5. Click **Run workflow** button

The workflow will:
1. Build and verify the project
2. Sign all artifacts with GPG
3. Deploy to Sonatype OSSRH
4. Automatically release to Maven Central (staged and promoted)

### Manual Local Publishing

For testing or manual release:

```bash
# Ensure you have GPG set up locally
gpg --list-secret-keys

# Configure Maven settings.xml with OSSRH credentials
# Location: ~/.m2/settings.xml

cat > ~/.m2/settings.xml << 'EOF'
<settings>
  <servers>
    <server>
      <id>ossrh</id>
      <username>YOUR_OSSRH_USERNAME</username>
      <password>YOUR_OSSRH_TOKEN</password>
    </server>
  </servers>
</settings>
EOF

# Build and deploy to Maven Central
./mvnw clean deploy -Prelease

# Or with specific GPG key
./mvnw clean deploy -Prelease -Dgpg.keyname=YOUR_KEY_ID
```

## POM Configuration

The POM is configured with all required Maven Central metadata:

### Required Metadata ✅
- [x] `groupId`: `vc.tbc`
- [x] `artifactId`: `tbc-bq-jdbc`
- [x] `version`: `1.0.39` (incremented automatically)
- [x] `name`: BigQuery JDBC Driver
- [x] `description`: Modern JDBC driver for Google BigQuery
- [x] `url`: https://github.com/Two-Bear-Capital/tbc-bq-jdbc
- [x] `licenses`: Apache 2.0
- [x] `developers`: Two Bear Capital
- [x] `scm`: Git connection and URL

### Required Artifacts ✅
- [x] Main JAR
- [x] Sources JAR (`-sources.jar`)
- [x] Javadoc JAR (`-javadoc.jar`)
- [x] GPG signatures (`.asc` files for all JARs)
- [x] Shaded JAR variants (optional, for convenience)

## Release Profile

The `release` profile activates when deploying to Maven Central:

```bash
./mvnw clean deploy -Prelease
```

This profile:
- Enables GPG signing of all artifacts
- Configures Nexus Staging plugin for OSSRH
- Sets `autoReleaseAfterClose=true` (automatic promotion)

## Artifact Variants

Published artifacts:

| Artifact | Classifier | Description |
|----------|-----------|-------------|
| `tbc-bq-jdbc-1.0.42.jar` | *(none)* | Slim JAR (60KB, requires dependencies) |
| `tbc-bq-jdbc-1.0.42.jar` | `shaded` | Fat JAR with all dependencies (38MB) |
| `tbc-bq-jdbc-1.0.42-with-logging.jar` | `with-logging` | Fat JAR + Logback for IntelliJ (39MB) |
| `tbc-bq-jdbc-1.0.42.jar` | `sources` | Source code |
| `tbc-bq-jdbc-1.0.42.jar` | `javadoc` | Javadoc |

Each artifact is signed with GPG (`.asc` signature files).

## Maven Central Sync Timeline

1. **Deploy** → Artifacts uploaded to OSSRH staging repository
2. **Stage** → Artifacts validated (POM, signatures, required files)
3. **Release** → Promoted from staging to Maven Central
4. **Sync** → Synced to Maven Central (can take 15 minutes to 2 hours)
5. **Search Index** → Available in Maven Central search (up to 4 hours)

**Check status:**
- Sonatype: https://s01.oss.sonatype.org/#stagingRepositories
- Maven Central: https://central.sonatype.com/artifact/vc.tbc/tbc-bq-jdbc
- Maven Search: https://search.maven.org/artifact/vc.tbc/tbc-bq-jdbc

## Usage After Publishing

Once published, users can add the driver as a Maven dependency:

### Maven
```xml
<dependency>
    <groupId>vc.tbc</groupId>
    <artifactId>tbc-bq-jdbc</artifactId>
    <version>1.0.42</version>
</dependency>

<!-- Or use the shaded variant (includes all dependencies) -->
<dependency>
    <groupId>vc.tbc</groupId>
    <artifactId>tbc-bq-jdbc</artifactId>
    <version>1.0.42</version>
    <classifier>shaded</classifier>
</dependency>
```

### Gradle
```groovy
dependencies {
    implementation 'vc.tbc:tbc-bq-jdbc:1.0.42'

    // Or shaded variant
    implementation 'vc.tbc:tbc-bq-jdbc:1.0.42:shaded'
}
```

## Troubleshooting

### GPG Signing Fails

```bash
# Verify GPG is installed
gpg --version

# List available keys
gpg --list-secret-keys

# Test signing
echo "test" | gpg --clearsign

# If pinentry fails in CI, ensure gpgArguments includes --pinentry-mode loopback
```

### Sonatype Deployment Fails

Common issues:
1. **Invalid credentials**: Check `OSSRH_USERNAME` and `OSSRH_TOKEN` secrets
2. **Namespace not approved**: Verify namespace claim at https://central.sonatype.com/
3. **Missing signatures**: Ensure GPG signing is enabled (`-Prelease` profile)
4. **Invalid POM**: All required fields must be present (license, developers, scm)

### Artifacts Not Appearing in Maven Central

1. Check OSSRH staging repository: https://s01.oss.sonatype.org/
2. Verify automatic release succeeded (check workflow logs)
3. Wait up to 2 hours for sync to complete
4. Check for validation errors in OSSRH UI

## References

- [Sonatype Central Portal Guide](https://central.sonatype.org/register/central-portal/)
- [Publishing via GitHub Actions](https://central.sonatype.org/publish/publish-portal-github-actions/)
- [Requirements for Maven Central](https://central.sonatype.org/publish/requirements/)
- [GPG Signing Guide](https://central.sonatype.org/publish/requirements/gpg/)

## Support

For issues with:
- **Publishing process**: Open issue on GitHub
- **Sonatype account**: Contact https://central.sonatype.org/support/
- **Maven Central availability**: Check https://status.maven.org/
