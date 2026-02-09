# Maven Central Publishing Setup - Summary

## What Was Configured

### 1. POM Configuration ✅

**Added plugin versions:**
- `maven-gpg-plugin` 3.2.9 - For GPG signing
- `nexus-staging-maven-plugin` 1.7.0 - For Maven Central deployment

**Updated `distributionManagement`:**
```xml
<distributionManagement>
    <repository>
        <id>github</id>
        <name>GitHub Packages</name>
        <url>https://maven.pkg.github.com/Two-Bear-Capital/tbc-bq-jdbc</url>
    </repository>
    <snapshotRepository>
        <id>ossrh</id>
        <name>Sonatype Nexus Snapshots</name>
        <url>https://s01.oss.sonatype.org/content/repositories/snapshots</url>
    </snapshotRepository>
</distributionManagement>
```

**Created `release` profile:**
- Activates GPG signing
- Configures Nexus Staging plugin
- Sets `autoReleaseAfterClose=true` for automatic release
- Overrides `distributionManagement` to use OSSRH releases endpoint

### 2. GitHub Actions Workflow ✅

**Created:** `.github/workflows/publish-maven-central.yml`

This workflow:
- Can be triggered manually or on GitHub release
- Builds and verifies the project
- Signs artifacts with GPG
- Deploys to Maven Central
- Provides deployment summary

### 3. Documentation ✅

**Created:** `docs/MAVEN_CENTRAL_PUBLISHING.md`

Comprehensive guide covering:
- Prerequisites (Sonatype account, GPG key setup)
- GitHub Secrets configuration
- Publishing process (automatic and manual)
- Troubleshooting
- Timeline and verification

## Required GitHub Secrets

You need to configure these secrets in your GitHub repository before publishing:

| Secret Name | Description | How to Obtain |
|------------|-------------|---------------|
| `OSSRH_USERNAME` | Sonatype account username | Your Sonatype Central username |
| `OSSRH_TOKEN` | Sonatype account token | Generate at https://central.sonatype.com/account |
| `GPG_PRIVATE_KEY` | GPG private key (ASCII-armored) | Export with `gpg --export-secret-keys -a KEY_ID` |
| `GPG_PASSPHRASE` | GPG key passphrase | Passphrase you set when creating GPG key |

## Next Steps

### Step 1: Create Sonatype Account ⏳

1. Go to https://central.sonatype.com/
2. Sign up for an account
3. Verify your email

### Step 2: Claim Namespace ⏳

**Option A: GitHub Verification (Recommended)**
1. In Sonatype Central Portal, go to "Namespaces"
2. Click "Add Namespace"
3. Enter `vc.tbc`
4. Select verification method: "GitHub"
5. Follow instructions to verify GitHub organization ownership
6. Wait for approval (1-2 business days)

**Option B: Domain Verification**
1. In Sonatype Central Portal, go to "Namespaces"
2. Click "Add Namespace"
3. Enter `vc.tbc`
4. Select verification method: "DNS TXT Record"
5. Add DNS TXT record to `tbc.com` domain
6. Wait for verification

### Step 3: Generate GPG Key ⏳

```bash
# Generate GPG key (use defaults, 4096-bit RSA)
gpg --full-generate-key

# Follow prompts:
# - Name: Two Bear Capital
# - Email: (email associated with Sonatype account)
# - Passphrase: (strong passphrase - save this!)

# List keys to get key ID
gpg --list-secret-keys --keyid-format=long

# Output example:
# sec   rsa4096/3AA5C34371567BD2 2024-01-01 [SC]
#                 ^^^^^^^^^^^^^^^^ (this is your KEY_ID)

# Publish public key to keyservers
gpg --keyserver keys.openpgp.org --send-keys YOUR_KEY_ID
gpg --keyserver keyserver.ubuntu.com --send-keys YOUR_KEY_ID

# Export private key for GitHub Actions
gpg --export-secret-keys -a YOUR_KEY_ID > gpg-private.key

# The file gpg-private.key now contains your private key
# You'll paste its contents into GitHub Secrets
```

### Step 4: Generate Sonatype Token ⏳

1. Log in to https://central.sonatype.com/
2. Go to **Account** → **Generate User Token**
3. Click **Generate**
4. Save the username and password (token) shown

### Step 5: Configure GitHub Secrets ⏳

1. Go to your GitHub repository
2. Navigate to **Settings** → **Secrets and variables** → **Actions**
3. Click **New repository secret**
4. Add each secret:

```bash
Name: OSSRH_USERNAME
Secret: (your Sonatype username)

Name: OSSRH_TOKEN
Secret: (your Sonatype token from Step 4)

Name: GPG_PRIVATE_KEY
Secret: (paste contents of gpg-private.key file)

Name: GPG_PASSPHRASE
Secret: (your GPG key passphrase from Step 3)
```

### Step 6: Test Publishing (Optional) ⏳

After all secrets are configured, test the publishing process:

1. Go to **Actions** tab in GitHub
2. Select **Publish to Maven Central** workflow
3. Click **Run workflow**
4. Leave version empty (will use POM version)
5. Click **Run workflow** button
6. Monitor the workflow execution

### Step 7: Verify Publication ⏳

After successful deployment (15 min - 2 hours):

1. **Sonatype Staging:** https://s01.oss.sonatype.org/
2. **Maven Central Search:** https://central.sonatype.com/artifact/vc.tbc/tbc-bq-jdbc
3. **Maven Search:** https://search.maven.org/artifact/vc.tbc/tbc-bq-jdbc

## Publishing Workflow

### Development Workflow

1. **Push to `main`** → Automatic version bump and GitHub Packages publish
2. **Manual Maven Central publish** → Run GitHub Action workflow
3. **Artifacts available:**
   - GitHub Packages: Immediate
   - Maven Central: 15 min - 2 hours

### Release Workflow

```bash
# Local development
git checkout develop
# ... make changes ...
git commit -m "Add new feature"
git push origin develop

# Merge to main (via PR)
gh pr create --base main --head develop
gh pr merge --auto

# Wait for automatic version bump and GitHub Packages publish
# Triggered by: .github/workflows/version-and-release.yml

# Manually trigger Maven Central publish
# Go to Actions → Publish to Maven Central → Run workflow
```

## Maven Coordinates

Once published, users can add the dependency:

### Maven
```xml
<dependency>
    <groupId>vc.tbc</groupId>
    <artifactId>tbc-bq-jdbc</artifactId>
    <version>1.0.43</version>
</dependency>
```

### Gradle
```groovy
dependencies {
    implementation 'vc.tbc:tbc-bq-jdbc:1.0.43'
}
```

## Troubleshooting

### Common Issues

**1. "Unauthorized" error during deploy**
- Verify `OSSRH_USERNAME` and `OSSRH_TOKEN` secrets are correct
- Regenerate token at https://central.sonatype.com/account

**2. "Invalid signature" or GPG errors**
- Verify `GPG_PRIVATE_KEY` contains full ASCII-armored private key
- Ensure `GPG_PASSPHRASE` is correct
- Check that public key was published to keyservers

**3. "Namespace not allowed" error**
- Verify namespace claim for `vc.tbc` is approved in Sonatype Central
- Check https://central.sonatype.com/namespace/vc.tbc

**4. Artifacts not appearing in Maven Central**
- Wait up to 2 hours for sync
- Check OSSRH staging repository for errors
- Verify `autoReleaseAfterClose=true` in POM

## Security Notes

- **Never commit GPG private keys** to the repository
- **Rotate tokens periodically** for security
- **Use strong passphrases** for GPG keys
- **Limit access** to GitHub Secrets (repository admins only)

## Support

- **Maven Central issues:** https://central.sonatype.org/support/
- **Driver issues:** https://github.com/Two-Bear-Capital/tbc-bq-jdbc/issues
- **Documentation:** See `docs/MAVEN_CENTRAL_PUBLISHING.md`

## Cleanup After Setup

After successfully configuring GitHub Secrets, delete local GPG key export:

```bash
# Securely delete the exported private key
shred -u gpg-private.key  # Linux
srm gpg-private.key        # macOS (if installed)
rm -P gpg-private.key      # macOS (native)
```

Your GPG key remains safely in your local GPG keyring.
