# Maven Central Publishing Setup - Quick Start

## What Was Configured

This project is now set up for publishing to **Maven Central** using the modern Central Portal approach.

### 1. POM Configuration ✅

**Added:** `central-publishing-maven-plugin` (v0.9.0)
- Replaces legacy OSSRH/Nexus Staging approach
- Handles bundle creation, validation, and publishing automatically
- Configured with `autoPublish=true` for CI/CD

**GPG Signing:** Configured via `maven-gpg-plugin` (v3.2.9)
- Signs all artifacts (JARs, sources, javadoc)
- Activated in `release` profile

**Maven Coordinates:**
- Group ID: `vc.tbc`
- Artifact ID: `tbc-bq-jdbc`
- Current Version: `1.0.43`

### 2. GitHub Actions Workflow ✅

**File:** `.github/workflows/publish-maven-central.yml`

**Triggers:**
- Manual: Go to Actions → Publish to Maven Central → Run workflow
- Automatic: On GitHub release (optional)

**What it does:**
1. Builds and verifies the project
2. Signs artifacts with GPG
3. Deploys to Maven Central via Central Portal
4. Auto-publishes after validation
5. Waits until artifacts are available

### 3. Documentation ✅

**Created:** `docs/MAVEN_CENTRAL_PUBLISHING.md`

Comprehensive guide covering:
- Modern Central Portal setup
- Namespace verification (GitHub or DNS)
- GPG key generation and management
- GitHub Secrets configuration
- Publishing workflow
- Troubleshooting

## Required GitHub Secrets

Configure these **four secrets** in your repository:

**Settings → Secrets and variables → Actions → New repository secret**

| Secret Name | Value | How to Get |
|------------|-------|------------|
| `MAVEN_CENTRAL_USERNAME` | Token username (not email!) | [Generate User Token](https://central.sonatype.com/account) |
| `MAVEN_CENTRAL_TOKEN` | Token password | [Generate User Token](https://central.sonatype.com/account) |
| `GPG_PRIVATE_KEY` | ASCII-armored private key | `gpg --export-secret-keys -a KEY_ID` |
| `GPG_PASSPHRASE` | GPG key passphrase | From GPG key creation |

## Quick Setup Checklist

### Step 1: Create Central Portal Account (5 min) ⏳

1. Visit https://central.sonatype.com/
2. Sign up (GitHub, Google, or email)
3. Verify your email

### Step 2: Verify Namespace (1-2 days) ⏳

**Option A: GitHub Verification (Recommended, instant)**
1. Go to [Namespaces](https://central.sonatype.com/publishing/namespaces)
2. Click **Add Namespace** → Enter `vc.tbc`
3. Select **GitHub** verification
4. Create verification repository as instructed
5. Verification happens automatically

**Option B: Domain Verification (if you control tbc.vc)**
1. Go to [Namespaces](https://central.sonatype.com/publishing/namespaces)
2. Click **Add Namespace** → Enter `vc.tbc`
3. Select **DNS TXT Record** verification
4. Add TXT record to `tbc.vc` domain
5. Click **Verify**

### Step 3: Generate User Token (1 min) ⏳

1. Log in to https://central.sonatype.com/
2. Click your username → **View Account**
3. Click **Generate User Token**
4. Save both values:
   - Username: `abcd1234` (NOT your email)
   - Password: `randomtokenstring`

### Step 4: Generate GPG Key (5 min) ⏳

```bash
# Generate 4096-bit RSA key
gpg --full-generate-key
# Choose: RSA and RSA, 4096 bits, never expire
# Name: Two Bear Capital
# Email: (your Central Portal email)
# Passphrase: (strong passphrase - save it!)

# Get your key ID
gpg --list-secret-keys --keyid-format=long
# Example output: rsa4096/3AA5C34371567BD2
#                          ^^^^^^^^^^^^^^^^ (your KEY_ID)

# Publish to keyservers
gpg --keyserver keys.openpgp.org --send-keys YOUR_KEY_ID
gpg --keyserver keyserver.ubuntu.com --send-keys YOUR_KEY_ID

# Export for GitHub Actions
gpg --export-secret-keys -a YOUR_KEY_ID > gpg-private.key
```

**IMPORTANT:** The exported key file starts with `-----BEGIN PGP PRIVATE KEY BLOCK-----`

### Step 5: Configure GitHub Secrets (2 min) ⏳

1. Go to your GitHub repository
2. **Settings → Secrets and variables → Actions**
3. Click **New repository secret** for each:

```
Name: MAVEN_CENTRAL_USERNAME
Value: (token username from Step 3, e.g., "abcd1234")

Name: MAVEN_CENTRAL_TOKEN
Value: (token password from Step 3)

Name: GPG_PRIVATE_KEY
Value: (paste entire contents of gpg-private.key file)

Name: GPG_PASSPHRASE
Value: (GPG passphrase from Step 4)
```

### Step 6: Publish to Maven Central (15-45 min) ⏳

1. Go to **Actions** tab in GitHub
2. Select **Publish to Maven Central** workflow
3. Click **Run workflow**
4. Leave version empty (uses POM version)
5. Click **Run workflow** button
6. Wait for completion

**Timeline:**
- Build and sign: 2-3 minutes
- Upload to Central Portal: 1-2 minutes
- Validation: 1-3 minutes
- Publishing: 10-30 minutes
- **Total:** 15-45 minutes

### Step 7: Verify Publication ⏳

After workflow completes:

1. **Central Portal Deployments:**
   https://central.sonatype.com/publishing/deployments

2. **Artifact Page:**
   https://central.sonatype.com/artifact/vc.tbc/tbc-bq-jdbc

3. **Maven Central (after 10-30 min):**
   https://repo1.maven.org/maven2/vc/tbc/tbc-bq-jdbc/

4. **Maven Search (after 1-2 hours):**
   https://search.maven.org/artifact/vc.tbc/tbc-bq-jdbc

### Step 8: Cleanup ⏳

```bash
# Securely delete exported GPG key
shred -u gpg-private.key  # Linux
rm -P gpg-private.key      # macOS

# GPG key remains in your local keyring
gpg --list-secret-keys
```

## Publishing Strategy

### Dual Repository Publishing

The project publishes to **two repositories**:

| Repository | When | How | Purpose |
|-----------|------|-----|---------|
| **GitHub Packages** | Every push to `main` | Automatic | Internal/CI use |
| **Maven Central** | Manual trigger | GitHub Actions | Public distribution |

**Workflow:**
1. Push to `main` → Auto version bump → GitHub Packages publish
2. Manually trigger → Maven Central publish
3. Both repositories have same artifacts

## Usage After Publishing

Once published to Maven Central, users can add:

### Maven

```xml
<dependency>
    <groupId>vc.tbc</groupId>
    <artifactId>tbc-bq-jdbc</artifactId>
    <version>1.0.48</version>
</dependency>
```

### Gradle

```groovy
dependencies {
    implementation 'vc.tbc:tbc-bq-jdbc:1.0.48'
}
```

### Direct Download

```bash
wget https://repo1.maven.org/maven2/vc/tbc/tbc-bq-jdbc/1.0.48/tbc-bq-jdbc-1.0.48.jar
```

## Troubleshooting

### "401 Unauthorized"
- Verify `MAVEN_CENTRAL_USERNAME` and `MAVEN_CENTRAL_TOKEN` are correct
- Regenerate token at https://central.sonatype.com/account

### "Invalid signature"
- Check `GPG_PRIVATE_KEY` contains full ASCII-armored key (starts with `-----BEGIN PGP`)
- Verify `GPG_PASSPHRASE` is correct

### "Namespace not verified"
- Visit https://central.sonatype.com/publishing/namespaces
- Ensure `vc.tbc` namespace is verified

### Artifacts not in search
- Normal! Search index takes 1-2 hours
- Artifacts are available for download immediately
- Check: https://repo1.maven.org/maven2/vc/tbc/tbc-bq-jdbc/

## Key Differences from Legacy OSSRH

If you're familiar with the old process:

| Old Way (OSSRH) | New Way (Central Portal) |
|----------------|------------------------|
| `nexus-staging-maven-plugin` | `central-publishing-maven-plugin` |
| Server ID: `ossrh` | Server ID: `central` |
| JIRA ticket for namespace | Self-service namespace verification |
| Manual staging in Nexus UI | Automatic staging and release |
| `s01.oss.sonatype.org` | `central.sonatype.com` |
| Email/password auth | User token auth |

**The modern approach is simpler, faster, and fully automated.**

## Resources

- **Full Documentation:** `docs/MAVEN_CENTRAL_PUBLISHING.md`
- **Official Guide:** https://central.sonatype.org/publish/publish-portal-maven/
- **Central Portal:** https://central.sonatype.com/
- **Plugin Docs:** https://github.com/sonatype/central-publishing-maven-plugin
- **Support:** https://central.sonatype.org/support/

## Security Notes

- ✅ Never commit GPG keys or tokens to git
- ✅ Use GitHub Secrets for all credentials
- ✅ Rotate tokens annually
- ✅ Use strong GPG passphrases (20+ chars)
- ✅ Delete exported key files after setup
- ✅ Review deployment logs for anomalies

## Next Steps After First Publish

1. ✅ Add Maven Central badge to README
2. ✅ Update documentation with Maven coordinates
3. ✅ Announce release to users
4. ✅ Set up automated publishing on release tags (optional)
5. ✅ Monitor download stats in Central Portal

## Quick Reference

**Publish Command (local):**
```bash
./mvnw clean deploy -Prelease
```

**Publish via GitHub Actions:**
1. Go to Actions → Publish to Maven Central
2. Click Run workflow
3. Wait 15-45 minutes

**Check status:**
- Deployments: https://central.sonatype.com/publishing/deployments
- Artifacts: https://central.sonatype.com/artifact/vc.tbc/tbc-bq-jdbc
- Downloads: https://repo1.maven.org/maven2/vc/tbc/tbc-bq-jdbc/

**Get help:**
- Driver issues: https://github.com/Two-Bear-Capital/tbc-bq-jdbc/issues
- Central Portal: https://central.sonatype.org/support/
