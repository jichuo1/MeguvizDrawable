# Independent experimental shape provider

This is a separate Gradle root. The `provider` module is an independently
compiled third-party example; both modules resolve the library only from the
root project's staged Maven repository. The app's project dependency is the
example provider itself, not a substitution for either library artifact.

SPI major 1/schema 1 is experimental and supports only normalized, closed
polygons. Providers are explicitly registered with a namespaced ID, version,
dependency IDs and vertex budget. The registry rejects duplicate IDs, missing
dependencies, cycles, unsupported versions and budgets. Preparation occurs on
the caller thread, creates a provider via the registered factory, and returns
a copied read-only plan. Factories must return a fresh instance and must not
capture host objects; these behavioral obligations cannot be enforced against
arbitrary code. Unknown providers have no global caching or shared runtime.
This polygon vocabulary has zero declared outsets, no offscreen operations,
and requires a polygon-capable backend. The Android adapter uses independent
per-owner Paint/Path and supports software Canvas. It exposes solid fill only,
not tint, stroke, outline, dynamic metrics or arbitrary path commands.
`setTint`, a non-null `setTintList` and a non-null `setTintMode` are rejected;
clearing a null tint or tint mode is a no-op. The API 29 `setTintBlendMode`
path remains unverified and is not part of the supported contract.

First stage both candidate modules, then build and execute the independent
contracts (PowerShell requires quoted dotted properties):

```text
gradlew --offline -Pmeguviz.profile=android :core:publishCandidatePublicationToStagingRepository :android:publishCandidatePublicationToStagingRepository
gradlew --offline -p fixtures/provider :provider:check :app:verifyCandidateResolution :app:assembleRelease :app:assembleDebugAndroidTest
gradlew --offline -p fixtures/provider -Pprovider.metadata=pom :provider:check :app:verifyCandidateResolution
```

The provider contract exercises the separately resolved core artifact. The
instrumentation test exercises real Android software Canvas pixels and owner
isolation, if built and run on a device. Release enables R8. These are fixtures,
not public releases or a claim that RecipeDrawable supports arbitrary paths.
