# Independent artifact consumer

This is a separate Gradle root. It only resolves the two candidate artifacts
from the parent build's staging Maven repository; no project dependency or
composite build is used. The version is a local candidate, not a release.

From the repository root, first publish both candidate modules to staging,
then run the independent consumer using `-p fixtures/consumer`. With JDK 17,
Gradle 9.8.2, Android SDK 37 and the necessary cached build tools:

```text
gradlew --offline -Pmeguviz.profile=android :core:publishCandidatePublicationToStagingRepository :android:publishCandidatePublicationToStagingRepository
gradlew --offline -p fixtures/consumer :app:verifyCandidateResolution :app:assembleRelease
gradlew --offline -p fixtures/consumer -Pconsumer.metadata=pom :app:verifyCandidateResolution :app:assembleRelease
```

The last run intentionally ignores Gradle metadata redirection and consumes
the Maven POM. On PowerShell, quote each `-P...` argument. The verifier fails
if the runtime resolution includes unexpected or substituted artifacts and
prints each artifact's SHA-256. Release uses R8; the resulting APK is unsigned
and is not a published application. A separately debug-signed copy of one
candidate has been installed and cold-started on one API 37 device; that does
not validate formal signing, rendered pixels or other devices.
