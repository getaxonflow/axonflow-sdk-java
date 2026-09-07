# release_level_api_floor

**Proves** the published jar's API floor is the **declared minimum JDK** - not
merely its language level and class-file version - so a consumer on that JDK
cannot get `NoSuchMethodError` out of a jar that compiled clean on ours.

**Prereqs**: a built tree (`mvn package`). Runs on any JDK; assertion 4's
second arm needs JDK >= 16 to show the gap, which is the PR board's JDK 17.

**Run**

```bash
mvn -q package -DskipTests
mvn -q dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt
CP="target/classes:$(cat /tmp/cp.txt)"

java -cp "$CP" runtime-e2e/release_level_api_floor/ReleaseLevelApiFloorTest.java
```

**Asserts**

1. `pom.xml` declares `maven.compiler.release` and does **not** declare
   `maven.compiler.source` / `maven.compiler.target`, nor wire them into the
   compiler plugin.
2. Every `examples/*/pom.xml` holds the same floor - the gate treats
   `examples/` as a user-facing surface and `Examples Compile` runs on JDK 17
   only, so the same hole existed there.
3. Every class in the packaged jar is at the class-file version for the
   declared floor (major 55 for Java 11).
4. **The point.** A post-floor API (`Stream.toList()`, Java 16+) is **refused**
   under `--release <floor>`, and - on a JDK >= 16 - **compiles** under
   `-source`/`-target <floor>`. Assertion 3 alone cannot catch this: built on
   JDK 11, `source`/`target` and `release` produce byte-identical class files.
   The difference only appears on a newer JDK, which is exactly what the PR
   board is.
5. The real public surface (`AxonFlow.create(AxonFlowConfig.builder()...)`)
   loads and links in this JVM from the built classes.

**Why it exists**

`maven.compiler.source`/`target` set the language level and the class-file
version but leave javac linking against the **running** JDK's class library. So
a Java 16+ API compiled clean on the PR board's JDK 17 and only failed on
main's JDK 11 lane - which is how four `Stream.toList()` calls reached main.

The asymmetry is structural, not bad luck: `ci.yml` runs the matrix `[17]` on
pull requests and only `[11, 17, 21]` on push-to-main, so the JDK 11 lane does
not exist at PR time. `release` pins the API surface on every JDK, moving the
detection to the PR board. This leg is what stops a future change from
reverting to `source`/`target` and reopening it.
