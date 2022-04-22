package io.quarkus.devtools.compat;


import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.ext.web.client.HttpResponse;
import io.vertx.mutiny.ext.web.client.WebClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import org.paukov.combinatorics3.Generator;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.stream.LogOutputStream;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.io.FileMatchers.aFileWithSize;

public class CliCompatTest {

    public static final Path STORAGE_FILE = Path.of("./storage/cli-compat-test.json");
    private static WebClient client = WebClient.create(Vertx.vertx());
    private static Storage storage;
    private static Set<Combination> verifiedCombinations = new HashSet<>();

    @BeforeAll
    public static void beforeAll() throws IOException {
        storage = readStorage();
        System.out.println("Already verified cli versions:\n" +String.join(",", storage.cliVersions));
        System.out.println("Already verified platform versions:\n" +String.join(",", storage.platformVersions));
    }

    public void store() throws IOException {
        Files.createDirectories(Path.of("./storage"));
        Files.writeString(STORAGE_FILE, JsonObject.mapFrom(storage).encodePrettily());
    }

    @TestFactory
    Stream<DynamicTest> testCli(@TempDir Path tempDir) throws IOException {
        final List<String> versions = client.getAbs("https://registry.quarkus.io/client/platforms/all")
            .send()
            .onItem().transform(HttpResponse::bodyAsJsonObject)
            .onItem().transform(CliCompatTest::extractVersions)
            .await().indefinitely();

        return versions.stream()
            .filter(v -> !(storage.platformVersions.contains(v) && storage.cliVersions.contains(v)))
            .limit(1)
            .flatMap(v -> testVersion(tempDir, versions, v))
            .filter(Objects::nonNull);
    }

    private static Storage readStorage() throws IOException {
        if (!Files.isRegularFile(STORAGE_FILE)) {
            return new Storage();
        }
        return (new JsonObject(Files.readString(STORAGE_FILE))).mapTo(Storage.class);
    }

    private static List<String> extractVersions(JsonObject o) {
        return o.getJsonArray("platforms").stream()
            .map(m -> (JsonObject) m)
            .flatMap(j -> j.getJsonArray("streams").stream())
            .map(m -> (JsonObject) m)
            .flatMap(j -> j.getJsonArray("releases").stream())
            .map(m -> (JsonObject) m)
            .map(j -> j.getString("version"))
            .filter(v -> v.contains("Final"))
            .collect(Collectors.toList());
    }

    private Stream<DynamicTest> testVersion(Path tempDir, List<String> allVersions, String version) {
        Set<Combination> platformTests = Generator.cartesianProduct(List.of(version), allVersions).stream()
            .map(i -> new Combination(i.get(0), i.get(1)))
            .collect(Collectors.toCollection(LinkedHashSet::new));
        DynamicTest platformTest = null;
        if(!storage.platformVersions.contains(version)) {
            platformTest = DynamicTest.dynamicTest("Testing Quarkus Platform " + version , () -> {
                testCombinations(tempDir, platformTests);
                storage.platformVersions.add(version);
                store();
            });
        }

        Set<Combination> cliTests = platformTests.stream().map(c -> new Combination(c.cli, c.platform)).collect(Collectors.toCollection(LinkedHashSet::new));
        DynamicTest cliTest = null;
        if(!storage.cliVersions.contains(version)) {
            cliTest = DynamicTest.dynamicTest("Testing Quarkus CLI " + version , () -> {
                testCombinations(tempDir, cliTests);
                storage.cliVersions.add(version);
                store();
            });
        }

        return Stream.of(platformTest, cliTest);
    }

    private void testCombinations(Path tempDir, Set<Combination> test) throws IOException, InterruptedException, TimeoutException {
        for (Combination c : test) {
            if (storage.ignoredCombinations.contains(c)) {
                System.out.println("This combination is set to be ignored: " + c);
                continue;
            }
            if (verifiedCombinations.contains(c)) {
                System.out.println("This combination has already been verified: " + c);
                continue;
            }
            testCLI(tempDir.resolve("cli_" + c.cli + "-platform_" + c.platform), c);
            verifiedCombinations.add(c);
        }
    }

    public void testCLI(Path tempDir, Combination combination) throws IOException, InterruptedException, TimeoutException {

        tempDir.toFile().mkdirs();

        String trust = jbang(tempDir, "trust", "add", "https://repo1.maven.org/maven2/io/quarkus/");

        assertThat(trust, matchesPattern("(?s).*Adding .https://repo1.maven.org/maven2/io/quarkus/. to .*/trusted-sources.json.*"));

        String appname = "qs-" + combination.cli.replace(".","_");
        String output = jbang(tempDir, "alias", "add", "-f", ".", "--name="+appname, "https://repo1.maven.org/maven2/io/quarkus/quarkus-cli/" + combination.cli + "/quarkus-cli-"+combination.cli +"-runner.jar");

        assertThat(output, matchesPattern(".jbang. Alias .* added .*\n"));

        String createResult = jbang(tempDir,appname, "create", "-P", "io.quarkus.platform::" + combination.platform, "demoapp");

        assertThat(tempDir.toFile(), aFileWithSize(greaterThan(1L)));

        int result = run(tempDir.resolve("demoapp"), "mvn", "clean", "package")
            .redirectOutputAlsoTo(new LogOutputStream() {
                @Override
                protected void processLine(String s) {
                    assertThat(s, not(matchesPattern("(?i)ERROR")));
                }
            }).execute().getExitValue();

        assertThat(result, equalTo(0));
    }

    String jbang(Path workingDir, String... args) throws IOException, InterruptedException, TimeoutException {
        List<String> realArgs = new ArrayList<>();
        realArgs.add(Path.of("./jbang").toAbsolutePath().toString());
        realArgs.addAll(Arrays.asList(args));

        return run(workingDir, realArgs.toArray(new String[0])).execute().outputUTF8();
    }

    ProcessExecutor run(Path workingDir, String... args) throws IOException, InterruptedException, TimeoutException {
        List<String> realArgs = new ArrayList<>();
        realArgs.addAll(Arrays.asList(args));

        System.out.println("run: " + String.join(" ", realArgs));
        return new ProcessExecutor().command(realArgs)
            .directory(workingDir.toFile())
            .redirectOutputAlsoTo(System.out)
            .exitValue(0)
            .readOutput(true);

    }

    static record Storage(Set<String> cliVersions, Set<String> platformVersions, Set<Combination> ignoredCombinations) {
        Storage() {
            this(new HashSet<>(), new HashSet<>(), new HashSet<>());
        }
    }

    static record Combination(String platform, String cli) {
    }


}
