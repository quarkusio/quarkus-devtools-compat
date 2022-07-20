package io.quarkus.devtools.compat;

import java.util.Set;

/**
 * Use this class to define Broken CLI or Platform versions
 *
 * Create an issue explaining why it is broken and add the link in comment.
 */
public final class BrokenVersion {

    private static final Set<String> BROKEN_CLI_VERSION = Set.of(
            // See https://github.com/quarkusio/quarkus-devtools-compat/issues/4
            "2.6.0.Final",
            "2.6.1.Final"
    );
    private static final Set<String> BROKEN_PLATFORM_VERSIONS = Set.of();

    public static boolean isBroken(TestUtils.Combination c) {
        return BROKEN_CLI_VERSION.contains(c.cli()) || BROKEN_PLATFORM_VERSIONS.contains(c.platform());
    }

}
