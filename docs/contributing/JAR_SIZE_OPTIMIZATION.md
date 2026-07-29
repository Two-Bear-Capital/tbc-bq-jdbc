# JAR Size Optimization

## Overview

The tbc-bq-jdbc shaded JARs have been optimized to reduce file size while maintaining full functionality. This document explains the optimization strategy and provides context about the driver's size.

## Current JAR Sizes

After optimization (measured on 1.0.110):
- **Standard shaded JAR**: 36.4 MB (was 38.2 MB before Arrow was dropped)
- **With-logging JAR**: 37.3 MB (was 39.1 MB)
- **Slim JAR**: 180 KB (dependencies supplied by the host classpath)

## Size Breakdown

### Native Libraries: ~25 MB (65% of total)

The largest component is platform-specific native libraries required for gRPC SSL/TLS and cryptographic operations:

**gRPC Netty TCNative (~18 MB):**
- Windows x86_64: 3.5 MB
- macOS x86_64: 3.0 MB
- macOS aarch64: 2.7 MB
- Linux x86_64: 2.9 MB
- Linux aarch64: 2.7 MB

**Conscrypt OpenJDK (~10 MB):**
- Windows x86_64: 2.7 MB
- Windows x86: 1.8 MB
- macOS x86_64: 2.8 MB
- Linux x86_64: 2.4 MB

**Netty Epoll (~200 KB):**
- Linux x86_64: 99 KB
- Linux aarch64: 108 KB

These native libraries are **required** for the BigQuery gRPC API to function correctly. All platform variants are bundled together so the driver works on any platform without requiring users to select platform-specific builds.

### Compiled Classes: ~11 MB (31% of total)

Essential runtime dependencies:
- Google Cloud BigQuery SDK
- gRPC and Protocol Buffers
- Jackson (JSON processing)
- HTTP client libraries
- OpenTelemetry
- Driver implementation (~500 KB)

### Resource Files: ~500 KB (1% of total)

After optimization:
- TZDB timezone data: 108 KB (required for date/time operations)
- SSL certificate data: 320 KB (required for secure connections)
- GraalVM reflection configs: 397 KB (enables native-image compilation)

## Implemented Optimizations

### 1. Exclude Protocol Buffer Definition Files (-750 KB)

**What:** Removed 107 `.proto` source files from shaded JARs.

**Why:** These are source definitions for protocol buffers. The compiled protobuf classes are already in the JAR, so source files are not needed at runtime.

**Implementation:**
```xml
<exclude>**/*.proto</exclude>
```

**Impact:**
- ✅ 750 KB size reduction (~2%)
- ✅ Zero runtime impact
- ✅ Safe and straightforward

### 2. Exclude Apache Arrow (-1.8 MB)

**What:** Excluded `org.apache.arrow:*` from both `google-cloud-bigquery` and
`google-cloud-bigquerystorage`, removing 999 classes (~4.9 MB uncompressed).

**Why:** Nothing in the driver referenced it — `grep -r org.apache.arrow src/main/java`
returns nothing. `StorageReadResultSet` requests `DataFormat.ARROW` from the Storage API
but never decodes the batches. Within `google-cloud-bigquerystorage`, only `StreamWriter`
(the *write* API, which the driver never uses) links Arrow classes.

Carrying it also left the classpath split across two incompatible Arrow majors:
`arrow-vector`/`arrow-memory-netty` resolved to 19.0.0 while `arrow-memory-core`/
`arrow-format` resolved to 17.0.0 via `libraries-bom`. Arrow does not support mixing
module versions, and that combination fails at runtime with
`Failed to initialize MemoryUtil`.

**Implementation:**
```xml
<exclusions>
    <exclusion>
        <groupId>org.apache.arrow</groupId>
        <artifactId>*</artifactId>
    </exclusion>
</exclusions>
```

Both artifacts need it — `google-cloud-bigquery` depends on `google-cloud-bigquerystorage`
itself, so excluding on the direct dependency alone leaves Arrow on the classpath.

**Impact:**
- ✅ 1.8 MB size reduction (~4.7%)
- ✅ Removes a broken mixed-version classpath
- ⚠️ Re-add matched `arrow-*` modules (one version across every module) when Arrow
  deserialization is implemented

### 3. Metadata Cleanup (Already in Place)

The Maven shade plugin configuration already implements:
- Signature file removal (*.SF, *.DSA, *.RSA)
- Duplicate manifest exclusion
- Maven metadata stripping
- Netty version file removal

### 4. Dependency Relocation (Already in Place)

All Google Cloud libraries are relocated to `vc.tbc.bq.jdbc.shaded.google` to prevent classpath conflicts. This increases size slightly but prevents version conflicts with user applications.

## Competitive Analysis: Simba BigQuery JDBC Driver

For context, the commercial Simba BigQuery JDBC driver:
- **Total distribution size**: 41.7 MB (70+ separate JAR files)
- **Native libraries**: 24 MB (57% of total)
- **Architecture**: Classpath-based (separate JARs for each dependency)

Our shaded JAR approach:
- ✅ **13% smaller** (36.4 MB vs 41.7 MB)
- ✅ **Simpler distribution** (1 JAR vs 70+ files)
- ✅ **Better for IntelliJ IDEA** (single file upload)
- ✅ **Prevents classpath conflicts** (dependencies relocated)
- ✅ **Faster startup** (fewer JARs to load)

Both drivers face the same fundamental constraint: gRPC requires platform-specific native libraries for SSL/TLS, accounting for ~60% of total size in both cases.

## Why the JAR is Large

The size is fundamentally driven by:

1. **Multi-platform native library support** (25 MB): Required for gRPC to work on Windows, macOS (x86_64 + aarch64), and Linux (x86_64 + aarch64)
2. **Google Cloud SDK complexity** (11 MB): Comprehensive BigQuery API support with extensive features
3. **Enterprise-grade security**: Full SSL/TLS support with all cryptographic libraries

This is expected and competitive for enterprise-grade JDBC drivers using modern gRPC-based APIs.

## Considered but Not Implemented

### Platform-Specific Builds

**Potential savings**: 20-22 MB per platform

**Why not implemented:**
- ❌ Complicates primary use case (IntelliJ IDEA driver installation)
- ❌ Users must know their OS and architecture
- ❌ More build artifacts to maintain and publish
- ❌ Cross-platform testing requires multiple JARs

**Verdict:** Trade-offs don't justify complexity for current use cases. Could revisit if demand emerges for server/CLI deployments.

### JAR Minimization

**Potential savings**: 5-10 MB

**Why not implemented:**
- ❌ **HIGH RISK**: Google Cloud SDK uses extensive reflection
- ❌ Could break at runtime in subtle, hard-to-debug ways
- ❌ Requires extensive testing across all features

**Verdict:** Too risky for production. Reflection-heavy libraries like Google Cloud SDK don't work well with bytecode minimization.

### Exclude GraalVM Metadata

**Potential savings**: 397 KB

**Why not implemented:**
- ❌ Breaks GraalVM native-image compilation
- ❌ Minimal savings (<1%)

**Verdict:** Not worth losing GraalVM compatibility.

## Recommendations for Users

### For IntelliJ IDEA Users

The standard 36.4 MB shaded JAR is appropriate. This is actually smaller and simpler than the Simba commercial driver.

### For Server/CLI Deployments

If JAR size is critical:
1. Use the slim JAR (180 KB) with dependencies on your classpath
2. Exclude unused platforms' native libraries if needed
3. Contact maintainers if platform-specific builds would be valuable

### For Native Image Compilation

The with-logging JAR (37.3 MB) includes GraalVM reflection metadata. Use this for native-image builds.

## Verification

After proto file exclusion, verify with:

```bash
# Build
./mvnw clean package

# Check sizes
ls -lh target/*.jar

# Verify proto files excluded (should return 0)
unzip -l target/tbc-bq-jdbc-3.1.0.jar | grep "\.proto$" | wc -l

# Verify Arrow excluded (both should return 0)
./mvnw dependency:tree | grep -c arrow
unzip -l target/tbc-bq-jdbc-3.1.0-shaded.jar | grep -c "org/apache/arrow"

# Run tests
./mvnw verify -Preal-integration-tests
```

## Conclusion

At 36.4 MB, the tbc-bq-jdbc shaded JAR is:
- ✅ **Competitive**: 13% smaller than Simba's 41.7 MB distribution
- ✅ **Optimized**: Protocol buffer source files excluded (like Simba)
- ✅ **Necessary**: 65% is essential native libraries for gRPC SSL/TLS
- ✅ **User-friendly**: Single JAR vs 70+ files

Further optimization would require risky approaches (minimization) or added complexity (platform-specific builds) that don't align with the driver's primary use case (IntelliJ IDEA integration).
