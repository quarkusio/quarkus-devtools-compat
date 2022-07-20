package io.quarkus.devtools.compat;

import io.vertx.core.json.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.util.Objects.isNull;

public class TestUtils {
    public static final String ECOSYSTEM_CI = "ECOSYSTEM_CI";
    private static final Path STORAGE_ROOT = Path.of("./storage");

    public static Combinations readStorageCombinations(String name)
            throws IOException {
       return readStorage(name, Combinations.class, Combinations::new);
    }

    public static <T> T readStorage(String name, Class<T> clazz, Supplier<T> defaultValueSupplier)
            throws IOException {
        final Path storage = STORAGE_ROOT.resolve(name);
        if (!Files.isRegularFile(storage)) {
            return defaultValueSupplier.get();
        }
        return (new JsonObject(Files.readString(storage))).mapTo(clazz);
    }

    public static <T> void writeToStorage(String name, T s) throws IOException {
        final Path storage = STORAGE_ROOT.resolve(name);
        Files.createDirectories(Path.of("./storage"));
        Files.writeString(storage, JsonObject.mapFrom(s).encodePrettily());
    }

    public static record Combinations(Set<Combination> values) {
        Combinations() {
            this(new HashSet<>());
        }


        public boolean contains(Combination c) {
            final Set<String> ignorePlatform = values.stream().filter(v -> isNull(v.cli())).map(Combination::platform)
                    .collect(Collectors.toSet());
            final Set<String> ignoreCli = values.stream().filter(v -> isNull(v.platform())).map(Combination::cli)
                    .collect(Collectors.toSet());
            return ignorePlatform.contains(c.platform()) || ignoreCli.contains(c.cli()) || values.contains(c);
        }

        public boolean notContains(Combination c) {
            return !contains(c);
        }
    }

    public static record Combination(String cli, String platform) {
    }


    public static boolean isEcosystemCI() {
        return Objects.equals(System.getenv(ECOSYSTEM_CI), "true");
    }
}
