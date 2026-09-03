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

Patch releases (X.Y.1, X.Y.2) can also remove internal APIs. When the plugin stops working after a
patch update, read `~/Library/Logs/JetBrains/IntelliJIdea<version>/idea*.log` and grep for
`Cannot create extension (class=com.github.njuro`. The `NoClassDefFoundError` names the removed
class. Inspect the current API in
`~/Applications/IntelliJ IDEA.app/Contents/plugins/DatabaseTools/lib/modules/intellij.database.core.impl.jar`
with `unzip -l` and `javap -p`.

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

### 7. Create the GitHub Release Manually
There is no working release workflow — GitHub Actions never runs in this repo, so the
release must be created manually and the built plugin zip attached to it:
```bash
gh release create vX.Y.Z --title "vX.Y.Z" --notes "- Updated to IntelliJ XXXX.X"
gh release upload vX.Y.Z build/distributions/postgres-vault-github-auth-X.Y.Z.zip
```

## Remaining Deprecation Warnings (as of 2026.2.2)
The plugin verifier reports 12 deprecated API usages, but all are inherited interface defaults:
- `isApplicable(LocalDataSource, ApplicabilityLevel)` -> bridge for `getApplicability(DatabaseConnectionPoint, ApplicabilityLevel)`
- `createWidget(...)` / `intercept(...)` / `loadAuthConfig(...)` / `saveAuthConfig(...)` -> default delegates for the current APIs

These cannot be eliminated from our side - they're default methods in the interface that bridge to the new APIs.

## API Migration Notes
- `getApplicability(DatabaseConnectionPoint, ApplicabilityLevel)` replaces `isApplicable(LocalDataSource)`
- `interceptConnection` is a suspend function returning `Boolean` (not `ProtoConnection`)
- Auth fields are declared, not built as Swing widgets: override
  `getAuthParams(config): List<DatabaseAuthProviderParam<*>>` and return
  `DatabaseAuthProviderParam.buildParams { textParam(...); passwordParam(...) }`
- `DatabaseAuthParameterBuilderHelper` holds the field helpers: `additionalPropertySerializer(key)`,
  `Serialiser<T>`, `UrlHandler<T>`, `CompletionProvider`
- `buildParams` lambda params are `textParam(id, name, completion, serializer, urlHandler)` and
  `passwordParam(id, name, serializer, urlHandler)` - the trailing arguments have defaults, so pass them by name
- Generic type arguments are platform types: `UrlHandler<String?>`, `Serialiser<OneTimeString?>`.
  Match the supertype parameter names (`v`, `model`) to avoid compiler warnings
- For fields not mapped to JDBC URL parameters, pass a no-op `UrlHandler`

## Removed APIs
- `com.intellij.database.dataSource.ui.AuthWidgetBuilder` and its nested `Serialiser`, `UrlHandler`
  and `configureWidget` extension were deleted in 2026.2.2 (build 262.10315). A plugin that
  references them fails to load with `NoClassDefFoundError` and the auth method silently
  disappears from the data source properties. Replacement: `getAuthParams` (see above).

## Version History Pattern
IntelliJ version -> build prefix mapping:
- 2024.3 -> 243
- 2025.1 -> 251
- 2025.2 -> 252
- 2025.3 -> 253
- 2026.1 -> 261
- 2026.2 -> 262
