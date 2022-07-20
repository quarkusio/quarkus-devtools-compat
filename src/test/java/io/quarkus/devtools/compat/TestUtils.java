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

    public static final class Combinations {
        private final Set<Combination> values;
        private final Set<String> ignorePlatform;
        private final Set<String> ignoreCli;

        @JsonCreator
        public Combinations(@JsonProperty("values") Set<Combination> values) {
            this.values = values;
            this.ignorePlatform = values.stream()
                    .filter(v -> Objects.equals(v.cli(), "*"))
                    .map(Combination::platform)
                    .collect(Collectors.toSet());
            this.ignoreCli = values.stream()
                    .filter(v -> Objects.equals(v.platform(), "*")).map(Combination::cli)
                    .collect(Collectors.toSet());
        }

        public Combinations() {
            this(new HashSet<>());
        }

        public boolean contains(Combination c) {
            return ignorePlatform.contains(c.platform()) || ignoreCli.contains(c.cli()) || values.contains(c);
        }

        public boolean notContains(Combination c) {
            return !contains(c);
        }

        @JsonGetter("values")
        public Set<Combination> values() {
            return values;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this)
                return true;
            if (obj == null || obj.getClass() != this.getClass())
                return false;
            var that = (Combinations) obj;
            return Objects.equals(this.values, that.values);
        }

        @Override
        public int hashCode() {
            return Objects.hash(values);
        }

        @Override
        public String toString() {
            return "Combinations[" +
                    "values=" + values + ']';
        }

    }

    public static record Combination(String cli, String platform) {
    }


    public static boolean isEcosystemCI() {
        return Objects.equals(System.getenv(ECOSYSTEM_CI), "true");
    }
}
