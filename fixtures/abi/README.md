# Frozen core consumer fixture

Compile `LegacyCoreClient.java` once against a saved earlier candidate JAR.
Retain the class file and its SHA-256, then run that same class file without
recompiling against the new JAR. Record both JAR hashes, the class hash, exit
codes and output. This checks only the APIs exercised by this fixture; it does
not establish complete binary compatibility or cover Android/provider code.

The first candidate is a development baseline, not a historical release.
