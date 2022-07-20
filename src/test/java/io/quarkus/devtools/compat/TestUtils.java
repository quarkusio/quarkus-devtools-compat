package io.quarkus.devtools.compat;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.vertx.core.json.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;


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

    public record Combinations(Set<Combination> values) {

        public Combinations() {
            this(new HashSet<>());
        }

        public boolean contains(Combination c) {
            return values.contains(c);
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
