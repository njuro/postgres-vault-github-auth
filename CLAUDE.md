# IntelliJ Plugin Upgrade Guide

## Project Overview
IntelliJ IDEA Ultimate plugin that fetches PostgreSQL credentials from Vault using GitHub tokens.
Build system: Gradle (Kotlin DSL). Source: Kotlin. Extension point: `com.intellij.database.connectionInterceptor`.

## How to Upgrade for a New IntelliJ Version

### 1. Find the New Version Numbers
Look up the following for the target IntelliJ release:
- **Build number** (e.g., `261.22158.277` for 2026.1) - use JetBrains data API:
  `https://data.services.jetbrains.com/products/releases?code=IIU&latest=true&type=release`
- **Build prefix** (first 3 digits of build number, e.g., `261` for 2026.1)
- **Latest Kotlin version** - check https://kotlinlang.org/docs/releases.html
- **Latest IntelliJ Platform Gradle Plugin** - check https://plugins.gradle.org/plugin/org.jetbrains.intellij.platform
- **Latest Gradle version** - check https://gradle.org/releases/
- **Latest Changelog plugin** - check https://plugins.gradle.org/plugin/org.jetbrains.changelog

### 2. Files to Update

**gradle.properties** - bump these values:
- `pluginVersion` - increment patch (e.g., 1.1.13 -> 1.1.14)
- `pluginSinceBuild` - build prefix (e.g., 261)
- `pluginUntilBuild` - build prefix with wildcard (e.g., 261.*)
- `pluginVerifierIdeVersions` - full build number (e.g., 261.22158.277)
- `platformVersion` - IntelliJ version (e.g., 2026.1)

**build.gradle.kts** - bump plugin versions:
- `org.jetbrains.kotlin.jvm` version
- `org.jetbrains.intellij.platform` version
- `org.jetbrains.changelog` version (if new version available)

**gradle/wrapper/gradle-wrapper.properties**:
- `distributionUrl` - update Gradle version

**CHANGELOG.md** - add new entry at top:
```
## [X.Y.Z]

- Updated to IntelliJ XXXX.X
```

### 3. Check for API Breaking Changes
Check https://plugins.jetbrains.com/docs/intellij/api-changes-list-2026.html (update year)
for breaking changes in database-related APIs:
- `DatabaseAuthProvider`
- `DatabaseConnectionInterceptor`
- `DatabaseCredentialsAuthProvider`
- `AuthWidget`

### 4. Fix Source Code (if needed)
Source files in `src/main/kotlin/com/github/njuro/postgresvaultgithub/`:
- `VaultAuth.kt` - main plugin logic (auth provider, widget config, connection interceptor)
- `Vault.kt` - vault CLI wrapper
- `VaultBundle.kt` - i18n

### 5. Build and Verify
```bash
./gradlew buildPlugin       # compile and package
./gradlew verifyPlugin      # run plugin verifier against target IDE
```

### 6. Commit, Tag, and Push
```bash
git add -A
git commit -m "CHORE: Update to IntelliJ XXXX.X"
git tag vX.Y.Z
git push origin main
git push origin vX.Y.Z
```
The `release.yml` GitHub Action triggers on tag push and publishes to JetBrains Marketplace.

## Remaining Deprecation Warnings (as of 2026.1)
The plugin verifier reports 8 deprecated API usages, but all are inherited interface defaults:
- `isApplicable(LocalDataSource)` -> inherited bridge method from `DatabaseAuthProvider`; our code uses `getApplicability(DatabaseConnectionPoint)` instead
- `createWidget()` / `intercept()` -> inherited default delegates; our code uses `configureWidget()` and `interceptConnection()` instead

These cannot be eliminated from our side - they're default methods in the interface that bridge to the new APIs.

## API Migration Notes
- `configureWidget` is a Kotlin extension function: `override fun AuthWidgetBuilder.configureWidget(...)`
- `interceptConnection` is a suspend function returning `Boolean` (not `ProtoConnection`)
- `AuthWidgetBuilder.additionalPropertySerializer(key)` handles save/load for text fields stored as additional datasource properties
- For password fields, use a custom `AuthWidgetBuilder.Serialiser<OneTimeString>` with `OneTimeString(String)` constructor
- URL handlers: use no-op `AuthWidgetBuilder.UrlHandler` implementations for fields not mapped to JDBC URL parameters

## Version History Pattern
IntelliJ version -> build prefix mapping:
- 2024.3 -> 243
- 2025.1 -> 251
- 2025.2 -> 252
- 2025.3 -> 253
- 2026.1 -> 261
