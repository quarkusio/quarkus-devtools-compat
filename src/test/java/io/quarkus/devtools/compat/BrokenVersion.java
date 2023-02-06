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

    public static final ArtifactVersion VERSION_3_0 = new DefaultArtifactVersion("3.0");
    public static final ArtifactVersion VERSION_2_11 = new DefaultArtifactVersion("2.11");
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

        return BROKEN_CLI_VERSION.contains(c.cli()) || BROKEN_PLATFORM_VERSIONS.contains(c.platform());
    }

}
