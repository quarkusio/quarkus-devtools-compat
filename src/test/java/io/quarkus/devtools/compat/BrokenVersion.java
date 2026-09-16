package io.quarkus.devtools.compat;

import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

import java.util.Set;

/**
 * Use this class to define Broken CLI or Platform versions
 *
 * Create an issue explaining why it is broken and add the link in comment.
 */
public final class BrokenVersion {

    public static final ArtifactVersion VERSION_2_11 = new DefaultArtifactVersion("2.11");
    public static final ArtifactVersion VERSION_3_0 = new DefaultArtifactVersion("3.0");
    public static final ArtifactVersion VERSION_3_5 = new DefaultArtifactVersion("3.5");
    public static final ArtifactVersion VERSION_4_0 = new DefaultArtifactVersion("4.0");
    private static final Set<String> BROKEN_CLI_VERSION = Set.of(
            // See https://github.com/quarkusio/quarkus-devtools-compat/issues/4
            "2.6.0.Final",
            "2.6.1.Final"
    );
    private static final Set<String> BROKEN_PLATFORM_VERSIONS = Set.of();

    public static boolean isBroken(TestUtils.Combination c) {
        final ArtifactVersion cliVersion = new DefaultArtifactVersion(c.cli());
        final ArtifactVersion platformVersion = new DefaultArtifactVersion(c.platform());

        if(platformVersion.compareTo(VERSION_3_0) >= 0 && cliVersion.compareTo(VERSION_2_11) < 0) {
            /**
             * CLI Versions below 2.10- are not compatible with Quarkus 3+ (see https://github.com/quarkusio/quarkus/issues/30914)
             */
            return true;
        }

        // Old CLIs (below 3.5) hang when resolving SNAPSHOT platform versions from the local repo
        if(c.platform().contains("SNAPSHOT") && cliVersion.compareTo(VERSION_3_5) < 0) {
            return true;
        }

        // 3.x CLIs are not compatible with Quarkus 4 (999-SNAPSHOT): quarkus-junit5 was renamed
        // https://github.com/quarkusio/quarkus/issues/56786
        if("999-SNAPSHOT".equals(c.platform()) && cliVersion.compareTo(VERSION_4_0) < 0) {
            return true;
        }

        return BROKEN_CLI_VERSION.contains(c.cli()) || BROKEN_PLATFORM_VERSIONS.contains(c.platform());
    }

}
