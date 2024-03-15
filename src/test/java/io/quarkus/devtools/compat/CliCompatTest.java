package io.quarkus.devtools.compat;


import io.quarkus.devtools.compat.TestUtils.Combination;
import io.quarkus.devtools.compat.TestUtils.Combinations;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.ext.web.client.HttpResponse;
import io.vertx.mutiny.ext.web.client.WebClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.paukov.combinatorics3.Generator;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.stream.LogOutputStream;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.quarkus.devtools.compat.TestUtils.ECOSYSTEM_CI;
import static io.quarkus.devtools.compat.TestUtils.isEcosystemCI;
import static io.quarkus.devtools.compat.TestUtils.readStorageCombinations;
import static java.util.function.Predicate.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.io.FileMatchers.aFileWithSize;

public class CliCompatTest {

    private static final String VERIFIED = "cli-compat-test/verified.json";
    private static final String TEST_FAILED = "cli-compat-test/test-failed.json";

    private static final String SNAPSHOT_VERSION = "999-SNAPSHOT";
    private static final String MAVEN_CENTRAL_QUARKUS_REPO = "https://repo1.maven.org/maven2/io/quarkus/";
    private static final String REGISTRY_VERSIONS_URL = "https://registry.quarkus.io/client/platforms/all";
    private static WebClient client = WebClient.create(Vertx.vertx());


    private static Storage storage;
    private static Set<Combination> tested = new HashSet<>();
    private static volatile boolean quarkusRepoTrusted = false;


    @BeforeAll
    public static void beforeAll() throws IOException {
        storage = readStorage();
    }

    @AfterAll
    public static void afterAll() throws IOException {
        final Set<Combination> testFailed = tested.stream().filter(storage.verified::notContains).collect(Collectors.toSet());
        if (!testFailed.isEmpty()) {
            storage.testFailed.values().addAll(testFailed);
            store();
        }
    }

    private static Storage readStorage() throws IOException {
        if (isEcosystemCI()) {
            return new Storage();
        }

        return new Storage(readStorageCombinations(VERIFIED), readStorageCombinations(TEST_FAILED));
    }

    public static void store() throws IOException {
        if (isEcosystemCI()) {
            return;
        }
        TestUtils.writeToStorage(VERIFIED, storage.verified);
        TestUtils.writeToStorage(TEST_FAILED, storage.testFailed);
    }

    @TestFactory
    @EnabledIfEnvironmentVariable(named = ECOSYSTEM_CI, matches = "true")
    Stream<DynamicTest> testCliSnapshot(@TempDir Path tempDir) throws IOException {
        final List<String> versions = fetchLatestVersionsFromRegistry();
        return versions.stream()
            .flatMap(v -> testSnapshot(tempDir, versions));
    }

    @TestFactory
    @DisabledIfEnvironmentVariable(named = ECOSYSTEM_CI, matches = "true")
    Stream<DynamicTest> testCliReleases(@TempDir Path tempDir) throws IOException {
        final List<String> versions = fetchAllVersionsFromRegistry();
        return versions.stream()
            .flatMap(v -> testVersions(tempDir, versions))
            .limit(storage.testFailed.values().size() + 10);
    }

    private static List<String> extractLatestVersions(JsonObject o) {
        return o.getJsonArray("platforms").stream()
            .map(m -> (JsonObject) m)
            .flatMap(j -> j.getJsonArray("streams").stream())
            .map(m -> (JsonObject) m)
            .map(j -> j.getJsonArray("releases").getJsonObject(0))
            .map(j -> j.getString("version"))
            .collect(Collectors.toList());
    }

    private static List<String> extractVersions(JsonObject o) {
        if(o == null) {
            return List.of();
        }
        return o.getJsonArray("platforms").stream()
            .map(m -> (JsonObject) m)
            .flatMap(j -> j.getJsonArray("streams").stream())
            .map(m -> (JsonObject) m)
            .flatMap(j -> j.getJsonArray("releases").stream())
            .map(m -> (JsonObject) m)
            .map(j -> j.getString("version"))
            .filter(v -> v.contains("Final") || v.matches("\\d+\\.\\d+\\.\\d+"))
            .collect(Collectors.toList());
    }

    private Stream<DynamicTest> testSnapshot(Path tempDir, List<String> allVersions) {
        List<String> allVersionAndSnapshot = new ArrayList<>(allVersions);
        allVersionAndSnapshot.add(SNAPSHOT_VERSION);
        return Generator.cartesianProduct(List.of(SNAPSHOT_VERSION), allVersionAndSnapshot).stream()
            .flatMap(i -> Stream.of(new Combination(i.get(0), i.get(1)), new Combination(i.get(1), i.get(0))))
            .filter(not(BrokenVersion::isBroken))
            .filter(storage.verified::notContains)
            .map(c -> testCombination(tempDir, c));
    }

    private Stream<DynamicTest> testVersions(Path tempDir, List<String> allVersions) {
        return Generator.cartesianProduct(allVersions, allVersions).stream()
            .map(i -> new Combination(i.get(0), i.get(1)))
            .filter(not(BrokenVersion::isBroken))
            .filter(storage.verified::notContains)
            .map(c -> testCombination(tempDir, c));
    }

    private DynamicTest testCombination(Path tempDir, Combination c) {
        return DynamicTest.dynamicTest("Test CLI " + c.cli() + " with Platform " + c.platform(), () -> {
            if (storage.testFailed.contains(c)) {
                System.out.println("This combination has already failed in a preview run: " + c);
                return;
            }
            if (storage.verified.contains(c)) {
                System.out.println("This combination has already been verified: " + c);
                return;
            }
            tested.add(c);
            testCLI(tempDir.resolve("cli_" + c.cli() + "-platform_" + c.platform()), c);
            storage.verified.values().add(c);
            store();
        });

    }

    public void testCLI(Path tempDir, Combination combination) throws IOException, InterruptedException, TimeoutException {
        tempDir.toFile().mkdirs();
        trustQuarkusRepo(tempDir);
        String appName = "qs-" + combination.cli().replace(".", "_");
        String repoDir =  Objects.equals(combination.cli(), SNAPSHOT_VERSION) ? getQuarkusMavenRepoLocal() : MAVEN_CENTRAL_QUARKUS_REPO;
        String output = jbang(tempDir, "alias", "add", "-f", ".", "--name=" + appName, repoDir + "quarkus-cli/" + combination.cli() + "/quarkus-cli-" + combination.cli() + "-runner.jar");
        final String platformGroup =  Objects.equals(combination.platform(), SNAPSHOT_VERSION) ? "io.quarkus" : "io.quarkus.platform";
        assertThat(output, matchesPattern(".jbang. Alias .* added .*\n"));
        List<String> commands = List.of(appName, "create", "app", "-P="+platformGroup + "::" + combination.platform(), "demoapp");
        propagateSystemPropertyIfSet("maven.repo.local", commands);
        String createResult = jbang(tempDir, commands);

        assertThat(tempDir.toFile(), aFileWithSize(greaterThan(1L)));
        List<String> mvnCommands = List.of("mvn", "clean", "package");
        propagateSystemPropertyIfSet("maven.repo.local", mvnCommands);
        int result = run(tempDir.resolve("demoapp"), mvnCommands)
            .redirectOutputAlsoTo(new LogOutputStream() {
                @Override
                protected void processLine(String s) {
                    assertThat(s, not(matchesPattern("(?i)ERROR")));
                }
            }).execute().getExitValue();

        assertThat(result, equalTo(0));
    }

    static String getQuarkusMavenRepoLocal() {
        return System.getProperty("maven.repo.local", System.getProperty("user.home", "~") + "/.m2/repository") + "/io/quarkus/";
    }

    static void trustQuarkusRepo(Path tempDir) throws IOException, InterruptedException, TimeoutException {
        if (!quarkusRepoTrusted) {
            String trust = jbang(tempDir, "trust", "add", MAVEN_CENTRAL_QUARKUS_REPO);
            assertThat(trust, matchesPattern("(?s).*Adding ." + MAVEN_CENTRAL_QUARKUS_REPO + ". to .*/trusted-sources.json.*"));
            quarkusRepoTrusted = true;
        }
    }

    static String jbang(Path workingDir, List<String> args) throws IOException, InterruptedException, TimeoutException {
        List<String> realArgs = new ArrayList<>();
        realArgs.add(Path.of("./jbang").toAbsolutePath().toString());
        realArgs.addAll(args);

        return run(workingDir, realArgs).execute().outputUTF8();
    }

    static String jbang(Path workingDir, String... args) throws IOException, InterruptedException, TimeoutException {
        return jbang(workingDir, Arrays.asList(args));
    }

    static ProcessExecutor run(Path workingDir, List<String> args) throws IOException, InterruptedException, TimeoutException {
        List<String> realArgs = new ArrayList<>();
        realArgs.addAll(args);

        System.out.println("run: " + String.join(" ", realArgs));
        return new ProcessExecutor().command(realArgs)
            .directory(workingDir.toFile())
            .redirectOutputAlsoTo(System.out)
            .exitValue(0)
            .readOutput(true);

    }

    private List<String> fetchLatestVersionsFromRegistry() {
        return client.getAbs(REGISTRY_VERSIONS_URL)
            .send()
            .onItem().transform(HttpResponse::bodyAsJsonObject)
            .onItem().transform(CliCompatTest::extractLatestVersions)
            .await().indefinitely();
    }

    private List<String> fetchAllVersionsFromRegistry() {
        return client.getAbs(REGISTRY_VERSIONS_URL)
            .send()
            .onItem().transform(HttpResponse::bodyAsJsonObject)
            .onItem().transform(CliCompatTest::extractVersions)
            .await().indefinitely();
    }


    private static void propagateSystemPropertyIfSet(String name, List<String> command) {
        if (System.getProperties().containsKey(name)) {
            final StringBuilder buf = new StringBuilder();
            buf.append("-D").append(name);
            final String value = System.getProperty(name);
            if (value != null && !value.isEmpty()) {
                buf.append("=").append(value);
            }
            command.add(buf.toString());
        }
    }

    static record Storage(Combinations verified, Combinations testFailed) {
        Storage() {
            this(new Combinations(), new Combinations());
        }
    }

}
