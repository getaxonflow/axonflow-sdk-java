import com.getaxonflow.sdk.AxonFlow;
import com.getaxonflow.sdk.AxonFlowConfig;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

/**
 * Runtime proof — the published jar's API floor is the DECLARED minimum JDK.
 *
 * <p>The failure this exists to prevent is a consumer on the declared minimum
 * JDK getting {@code NoSuchMethodError} out of a jar that compiled clean on
 * our machines. That happened: four {@code Stream.toList()} (Java 16+) calls
 * compiled on the PR board's JDK 17 and only failed on main's JDK 11 lane,
 * because {@code maven.compiler.source}/{@code target} set the language level
 * and the class-file version but leave javac linking against the RUNNING JDK's
 * class library. {@code release} pins the API surface too.
 *
 * <p>The PR matrix in ci.yml is {@code [17]} and only push-to-main runs
 * {@code [11, 17, 21]}, so that gap is structurally invisible until after
 * merge. This leg makes it visible on any JDK.
 *
 * <p>No stubs: it drives the real javac of the running JDK, reads the real
 * packaged jar, and constructs the real public client.
 */
public class ReleaseLevelApiFloorTest {

    /** Class-file major version for a given Java feature release: 11 -> 55. */
    private static int majorFor(int feature) {
        return feature + 44;
    }

    private static int failures = 0;

    private static void check(boolean ok, String what) {
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + what);
        if (!ok) {
            failures++;
        }
    }

    public static void main(String[] args) throws Exception {
        int running = Runtime.version().feature();
        System.out.println("Running JDK feature release: " + running);

        String pom = new String(Files.readAllBytes(Paths.get("pom.xml")));

        // ---- 1. The manifest declares `release`, and NOT the trap -----------
        System.out.println("\n[1] pom.xml declares the API floor via `release`");
        int declared = declaredRelease(pom);
        check(declared > 0, "maven.compiler.release is declared (found: " + declared + ")");
        check(!pom.contains("<maven.compiler.source>"),
                "maven.compiler.source is NOT declared");
        check(!pom.contains("<maven.compiler.target>"),
                "maven.compiler.target is NOT declared");
        check(!pom.contains("<source>${maven.compiler.source}</source>"),
                "compiler plugin does not wire <source>");
        check(!pom.contains("<target>${maven.compiler.target}</target>"),
                "compiler plugin does not wire <target>");

        // Every example the gate treats as user-facing must hold the same floor.
        System.out.println("\n[2] examples/*/pom.xml hold the same floor");
        Path examples = Paths.get("examples");
        if (Files.isDirectory(examples)) {
            List<Path> poms = Files.walk(examples, 2)
                    .filter(p -> p.getFileName().toString().equals("pom.xml"))
                    .sorted()
                    .collect(Collectors.toList());
            check(!poms.isEmpty(), "found example poms to check (" + poms.size() + ")");
            for (Path p : poms) {
                String body = new String(Files.readAllBytes(p));
                boolean clean = !body.contains("<maven.compiler.source>")
                        && !body.contains("<maven.compiler.target>");
                check(clean, p + " uses release, not source/target");
                int exDeclared = declaredRelease(body);
                check(exDeclared == declared,
                        p + " floor matches the SDK (" + exDeclared + " == " + declared + ")");
            }
        }

        // ---- 3. The packaged jar's class files are at the declared floor ----
        System.out.println("\n[3] packaged jar class-file version == " + majorFor(declared)
                + " (Java " + declared + ")");
        Path jar = newestJar();
        check(jar != null, "found a packaged jar under target/");
        if (jar != null) {
            System.out.println("      jar: " + jar);
            List<String> wrong = new ArrayList<>();
            int classes = 0;
            try (JarFile jf = new JarFile(jar.toFile())) {
                for (JarEntry e : java.util.Collections.list(jf.entries())) {
                    if (!e.getName().endsWith(".class")) {
                        continue;
                    }
                    classes++;
                    try (InputStream in = jf.getInputStream(e)) {
                        int major = majorOf(in);
                        if (major != majorFor(declared)) {
                            wrong.add(e.getName() + " -> major " + major);
                        }
                    }
                }
            }
            check(classes > 0, "jar contains class files (" + classes + ")");
            check(wrong.isEmpty(), "all " + classes + " classes are major "
                    + majorFor(declared)
                    + (wrong.isEmpty() ? "" : " — offenders: " + wrong.subList(0, Math.min(5, wrong.size()))));
        }

        // ---- 4. THE POINT: the floor is enforced against the API surface ----
        // A class-file-version check alone cannot catch the real bug: built on
        // JDK 11, source/target 11 and release 11 produce identical bytes. The
        // difference only shows on a NEWER JDK, which is what the PR board is.
        System.out.println("\n[4] a post-floor API is REFUSED by the project's compiler settings");
        Path probe = writeProbe();
        int withRelease = javac(probe, "--release", String.valueOf(declared));
        check(withRelease != 0,
                "Stream.toList() (Java 16+) FAILS to compile under --release " + declared);

        if (running >= 16) {
            int withSourceTarget = javac(probe,
                    "-source", String.valueOf(declared),
                    "-target", String.valueOf(declared));
            check(withSourceTarget == 0,
                    "...but COMPILES under -source/-target " + declared + " on this JDK "
                            + running + " — this is the hole `release` closes");
        } else {
            System.out.println("  SKIP  -source/-target arm needs a JDK >= 16 to show the gap"
                    + " (running " + running + "); run it on the PR board's JDK 17");
        }

        // ---- 5. The real public client loads and links here ----------------
        System.out.println("\n[5] the real public surface loads on this JVM");
        AxonFlow client = AxonFlow.create(AxonFlowConfig.builder()
                .endpoint(System.getenv().getOrDefault(
                        "AXONFLOW_E2E_PLATFORM_ENDPOINT", "http://localhost:8080"))
                .clientId("demo-client")
                .clientSecret("demo-secret")
                .build());
        check(client != null, "AxonFlow.create(...) constructed through the public builder");

        System.out.println();
        if (failures > 0) {
            System.out.println("RESULT: FAIL (" + failures + " assertion(s))");
            System.exit(1);
        }
        System.out.println("RESULT: PASS — API floor is Java " + declared
                + " and it is enforced at compile time on JDK " + running);
    }

    private static int declaredRelease(String pom) {
        String open = "<maven.compiler.release>";
        int i = pom.indexOf(open);
        if (i < 0) {
            return -1;
        }
        int j = pom.indexOf("</maven.compiler.release>", i);
        try {
            return Integer.parseInt(pom.substring(i + open.length(), j).trim());
        } catch (RuntimeException e) {
            return -1;
        }
    }

    private static int majorOf(InputStream in) throws IOException {
        DataInputStream d = new DataInputStream(in);
        d.readInt();      // 0xCAFEBABE
        d.readUnsignedShort(); // minor
        return d.readUnsignedShort();
    }

    private static Path newestJar() throws IOException {
        Path target = Paths.get("target");
        if (!Files.isDirectory(target)) {
            return null;
        }
        return Files.list(target)
                .filter(p -> p.getFileName().toString().endsWith(".jar"))
                .filter(p -> !p.getFileName().toString().endsWith("-sources.jar"))
                .filter(p -> !p.getFileName().toString().endsWith("-javadoc.jar"))
                .max((a, b) -> {
                    try {
                        return Files.getLastModifiedTime(a).compareTo(Files.getLastModifiedTime(b));
                    } catch (IOException e) {
                        return 0;
                    }
                })
                .orElse(null);
    }

    /** A probe using Stream.toList() — present since Java 16, absent in 11. */
    private static Path writeProbe() throws IOException {
        Path dir = Files.createTempDirectory("api-floor-probe");
        Path src = dir.resolve("Probe.java");
        Files.write(src, (""
                + "import java.util.List;\n"
                + "import java.util.stream.Stream;\n"
                + "public class Probe {\n"
                + "  static List<String> f() { return Stream.of(\"a\").toList(); }\n"
                + "}\n").getBytes());
        return src;
    }

    /** Runs the RUNNING JDK's javac on the probe; returns its exit code. */
    private static int javac(Path src, String... flags) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add(Paths.get(System.getProperty("java.home"), "bin", "javac").toString());
        for (String f : flags) {
            cmd.add(f);
        }
        cmd.add("-d");
        cmd.add(src.getParent().resolve("out").toString());
        cmd.add(src.toString());
        Process p = new ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start();
        String out = new String(readAll(p.getInputStream()));
        int rc = p.waitFor();
        for (String line : out.split("\n")) {
            if (!line.isEmpty()) {
                System.out.println("        javac| " + line);
            }
        }
        return rc;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            bo.write(buf, 0, n);
        }
        return bo.toByteArray();
    }
}
