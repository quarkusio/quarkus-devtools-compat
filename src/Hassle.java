///usr/bin/env jbang "$0" "$@" ; exit $?

//DEPS org.junit.jupiter:junit-jupiter-api:5.7.2
//DEPS org.junit.jupiter:junit-jupiter-params:5.7.2
//DEPS org.junit.jupiter:junit-jupiter-engine:5.7.2
//DEPS org.junit.platform:junit-platform-launcher:1.7.2
//DEPS org.slf4j:slf4j-simple:1.7.2
//DEPS org.zeroturnaround:zt-exec:1.12
//DEPS org.hamcrest:hamcrest:2.2
//DEPS com.github.dpaukov:combinatoricslib3:3.3.0

//JAVA 17

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.LoggingListener;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.paukov.combinatorics3.Generator;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.stream.LogOutputStream;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.System.out;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.io.FileMatchers.aFileWithSize;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

public class Hassle {

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
    String jbang(Path workingDir, String... args) throws IOException, InterruptedException, TimeoutException {
        List<String> realArgs = new ArrayList<>();
        realArgs.add("jbang");
        realArgs.addAll(Arrays.asList(args));

       return run(workingDir, realArgs.toArray(new String[0])).execute().outputUTF8();
    }

    // Define each Unit test here and run them separately in the IDE
   // @Test
    public void testCLI(Path tempDir, String cliVersion, String platformGV) throws IOException, InterruptedException, TimeoutException {

        tempDir.toFile().mkdirs();

        String trust = jbang(tempDir, "trust", "add", "https://repo1.maven.org/maven2/io/quarkus/");

        assertThat(trust, matchesPattern("(?s).*Adding .https://repo1.maven.org/maven2/io/quarkus/. to .*/trusted-sources.json.*"));

        String appname = "qs-" + cliVersion.replace(".","_");
        String output = jbang(tempDir, "alias", "add", "-f", ".", "--name="+appname, "https://repo1.maven.org/maven2/io/quarkus/quarkus-cli/" + cliVersion + "/quarkus-cli-"+cliVersion+"-runner.jar");

        assertThat(output, matchesPattern(".jbang. Alias .* added .*\n"));

        String createResult = jbang(tempDir,appname, "create", "-P", platformGV, "demoapp");

        assertThat(tempDir.toFile(), aFileWithSize(greaterThan(1L)));

        int result = run(tempDir.resolve("demoapp"), "quarkus", "build")
                .redirectOutputAlsoTo(new LogOutputStream() {
                    @Override
                    protected void processLine(String s) {
                        assertThat(s, not(matchesPattern("(?i)ERROR")));
                    }
                }).execute().getExitValue();

        assertThat(result, equalTo(0));
    }

    @TestFactory
    Stream<DynamicTest> dynTest(@TempDir Path tempDir) {

        record combi (String cli, String version, Path tempDir) {};

        List<String> versions = List.of(
                "2.7.3.Final", "2.6.3.Final", "2.2.3.Final","999-SNAPSHOT");

        List<String> cli = List.of(
                "2.7.3.Final",
                "2.5.0.Final",
                "2.3.0.Final"
        );

        List<combi> tests = Generator.cartesianProduct(cli, versions).stream().map(i ->
                new combi(i.get(0), "io.quarkus.platform::"+i.get(1), tempDir)).collect(Collectors.toList());

        return tests.stream()
                .map(c -> DynamicTest.dynamicTest(
                    "create-cli:"+c.cli()+"-platform:"+c.version(),
                        () -> {
                            testCLI(c.tempDir().resolve("cli_"+c.cli().replace(":","_")+"-platform_"+c.version().replace(":","_")), c.cli, c.version());
                        }
                ));
    }

    // Run all Unit tests with JBang with ./Hassle.java
    public static void main(final String... args) {
        final LauncherDiscoveryRequest request =
                LauncherDiscoveryRequestBuilder.request()
                        .selectors(selectClass(Hassle.class))
                        .build();
        final Launcher launcher = LauncherFactory.create();
        final LoggingListener logListener = LoggingListener.forBiConsumer((t,m) -> {
            System.out.println(m.get());
            if(t!=null) {
                t.printStackTrace();
            };
        });
        final SummaryGeneratingListener execListener = new SummaryGeneratingListener();
        launcher.registerTestExecutionListeners(execListener, logListener);
        launcher.execute(request);
        execListener.getSummary().printTo(new java.io.PrintWriter(out));
    }
}
