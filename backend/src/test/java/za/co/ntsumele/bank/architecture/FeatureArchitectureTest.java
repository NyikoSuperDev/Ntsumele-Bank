package za.co.ntsumele.bank.architecture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * class : FeatureArchitectureTest
 * Author : Nyiko Chauke
 * 2026
 *
 * Checks feature ownership, class-type package names, repository-only database access and separation of domain and API types.
 * Also guards service transport boundaries and detects circular imports between banking features without an additional test dependency.
 */
class FeatureArchitectureTest {

    private static final Path SOURCE_ROOT = Path.of("src/main/java/za/co/ntsumele/bank");
    private static final Set<String> BUSINESS_FEATURES = Set.of("account", "identity", "transaction", "dispute", "audit");
    private static final Set<String> ROOT_FEATURES = Set.of(
            "account", "identity", "transaction", "dispute", "audit", "http", "failure", "demo");
    private static final Set<String> FORBIDDEN_PACKAGES = Set.of(
            "utils", "util", "helper", "helpers", "manager", "managers", "common", "core", "base", "shared");
    private static final Map<String, String> TYPE_PACKAGES = Map.ofEntries(
            Map.entry("Controller.java", "controller"), Map.entry("Service.java", "service"),
            Map.entry("Repository.java", "repository"), Map.entry("Mapper.java", "mapper"),
            Map.entry("Config.java", "config"), Map.entry("Filter.java", "filter"),
            Map.entry("Policy.java", "policy"), Map.entry("Handler.java", "handler"),
            Map.entry("Writer.java", "writer"), Map.entry("Initializer.java", "initializer"));
    private static final Pattern DATA_ACCESS = Pattern.compile(
            "org\\.springframework\\.jdbc|javax\\.sql|java\\.sql|jakarta\\.persistence|\\bJdbcTemplate\\b");
    private static final Pattern SQL_STATEMENT = Pattern.compile(
            "\"{1,3}\\s*(?:SELECT\\b|INSERT\\s+INTO\\b|UPDATE\\s+\\w+\\s+SET\\b|DELETE\\s+FROM\\b)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern REST_CONTROLLER = Pattern.compile("@RestController\\b");
    private static final Pattern PACKAGE_DECLARATION = Pattern.compile("(?m)^package\\s+([a-zA-Z0-9_.]+);$");
    private static final Pattern FEATURE_IMPORT = Pattern.compile(
            "(?m)^import\\s+(?:static\\s+)?za\\.co\\.ntsumele\\.bank\\.([a-z][a-z0-9]*)\\.");
    private static final Pattern UPPER_LAYER_IMPORT = Pattern.compile(
            "(?m)^import\\s+[^;]*(?:Controller|Service|Request|Response|Mapper|Config);$");

    @Test
    void productionCodeBelongsToNamedFeaturesRatherThanTechnicalBuckets() throws IOException {
        try (var rootEntries = Files.list(SOURCE_ROOT)) {
            List<String> features = rootEntries.filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString()).toList();
            assertThat(features).containsAll(BUSINESS_FEATURES).allMatch(ROOT_FEATURES::contains);
        }
        for (Path javaFile : javaFiles()) {
            Path relativePath = SOURCE_ROOT.relativize(javaFile);
            String source = Files.readString(javaFile);
            String name = javaFile.getFileName().toString();
            if (relativePath.getNameCount() == 1) {
                assertThat(name).as("Only the application composes features at the root: %s", javaFile)
                        .isEqualTo("NtsumeleBankApplication.java");
            } else {
                assertThat(relativePath.getNameCount()).as("Expected feature/featureType/ClassName.java: %s", javaFile)
                        .isEqualTo(3);
                assertThat(relativePath.getName(0).toString()).as("Named feature: %s", javaFile)
                        .isIn(ROOT_FEATURES);
                String feature = relativePath.getName(0).toString();
                String role = typePackage(name, source);
                String expectedPackage = feature + Character.toUpperCase(role.charAt(0)) + role.substring(1);
                assertThat(relativePath.getName(1).toString()).as("Feature-prefixed class type package: %s", javaFile)
                        .isEqualTo(expectedPackage);
            }
            for (Path directory : relativePath.getParent() == null ? Path.of("") : relativePath.getParent()) {
                String packagePart = directory.toString();
                if (!packagePart.isEmpty()) {
                    assertThat(packagePart).matches("[a-z][a-zA-Z0-9]*").isNotIn(FORBIDDEN_PACKAGES);
                }
            }
            var packageMatcher = PACKAGE_DECLARATION.matcher(source);
            assertThat(packageMatcher.find()).as("Named package: %s", javaFile).isTrue();
            String suffix = relativePath.getParent() == null ? ""
                    : "." + relativePath.getParent().toString().replace('\\', '.').replace('/', '.');
            assertThat(packageMatcher.group(1)).isEqualTo("za.co.ntsumele.bank" + suffix);
            assertThat(javaFile.getFileName().toString()).doesNotEndWith("ServiceImpl.java").doesNotEndWith("Manager.java").doesNotEndWith("Helper.java");
        }
    }

    @Test
    void resourceControllersAndActionServicesDoNotAccessJdbcDirectly() throws IOException {
        int controllerCount = 0;
        int serviceCount = 0;
        for (Path javaFile : javaFiles()) {
            String source = Files.readString(javaFile);
            if (REST_CONTROLLER.matcher(source).find()) {
                controllerCount++;
                assertThat(source).as("Resource controller: %s", javaFile)
                        .contains("Service").doesNotContain("Repository");
                assertThat(DATA_ACCESS.matcher(source).find()).isFalse();
            }
            if (javaFile.getFileName().toString().endsWith("Service.java")) {
                serviceCount++;
                assertThat(source).as("Action service: %s", javaFile)
                        .doesNotContain("org.springframework.http", "org.springframework.web", "jakarta.servlet");
                assertThat(DATA_ACCESS.matcher(source).find()).isFalse();
            }
        }
        assertThat(controllerCount).isPositive();
        assertThat(serviceCount).isPositive();
    }

    @Test
    void repositoriesOwnSqlAndDependOnDomainRecordsRatherThanApiTypes() throws IOException {
        int repositoryCount = 0;
        for (Path javaFile : javaFiles()) {
            String source = Files.readString(javaFile);
            if (javaFile.getFileName().toString().endsWith("Repository.java")) {
                repositoryCount++;
                assertThat(source).as("Persistence repository: %s", javaFile)
                        .contains("@Repository").doesNotContain("org.springframework.http", "jakarta.servlet");
                assertThat(UPPER_LAYER_IMPORT.matcher(source).find())
                        .as("Repository must not depend on API or action service: %s", javaFile).isFalse();
            } else {
                assertThat(DATA_ACCESS.matcher(source).find())
                        .as("Only repositories access JDBC: %s", javaFile).isFalse();
                assertThat(SQL_STATEMENT.matcher(source).find())
                        .as("Only repositories contain SQL: %s", javaFile).isFalse();
            }
        }
        assertThat(repositoryCount).isPositive();
    }

    @Test
    void domainRecordsAndRequestResponseViewsHaveSeparateResponsibilities() throws IOException {
        int domainRecordCount = 0;
        int transportRecordCount = 0;
        for (Path javaFile : javaFiles()) {
            String source = Files.readString(javaFile);
            String name = javaFile.getFileName().toString();
            if (name.endsWith("Request.java") || name.endsWith("Response.java")) {
                transportRecordCount++;
                assertThat(source).as("Transport view: %s", javaFile)
                        .contains("public record").doesNotContain("JdbcTemplate", "@Repository", "@Service");
                assertThat(Pattern.compile("(?m)^import\\s+[^;]*(?:Repository|Service|Controller);$").matcher(source).find()).isFalse();
            } else if (source.contains("public record") || source.contains("public enum")) {
                domainRecordCount++;
                assertThat(source).as("Domain record: %s", javaFile)
                        .doesNotContain("org.springframework", "jakarta.servlet", "@Repository", "@Service");
                assertThat(UPPER_LAYER_IMPORT.matcher(source).find()).isFalse();
            }
            if (name.endsWith("Mapper.java")) {
                assertThat(source).as("Pure response mapper: %s", javaFile)
                        .doesNotContain("JdbcTemplate", "@Repository", "@Service", "jakarta.servlet");
            }
        }
        assertThat(domainRecordCount).isPositive();
        assertThat(transportRecordCount).isPositive();
    }

    @Test
    void businessFeaturesDoNotFormJavaDependencyCycles() throws IOException {
        Map<String, Set<String>> dependencies = new HashMap<>();
        BUSINESS_FEATURES.forEach(feature -> dependencies.put(feature, new LinkedHashSet<>()));
        for (Path javaFile : javaFiles()) {
            Path relativePath = SOURCE_ROOT.relativize(javaFile);
            if (relativePath.getNameCount() < 2) {
                continue;
            }
            String feature = relativePath.getName(0).toString();
            if (!BUSINESS_FEATURES.contains(feature)) {
                continue;
            }
            var imports = FEATURE_IMPORT.matcher(Files.readString(javaFile));
            while (imports.find()) {
                String dependency = imports.group(1);
                if (BUSINESS_FEATURES.contains(dependency) && !dependency.equals(feature)) {
                    dependencies.get(feature).add(dependency);
                }
            }
        }
        for (String feature : BUSINESS_FEATURES) {
            assertAcyclic(feature, dependencies, new ArrayList<>(), new HashSet<>());
        }
    }

    private void assertAcyclic(String feature, Map<String, Set<String>> dependencies,
                              List<String> path, Set<String> completed) {
        assertThat(path).as("Business feature dependency cycle: %s -> %s", path, feature).doesNotContain(feature);
        if (!completed.add(feature)) {
            return;
        }
        path.add(feature);
        for (String dependency : dependencies.get(feature)) {
            assertAcyclic(dependency, dependencies, path, completed);
        }
        path.remove(path.size() - 1);
    }

    private List<Path> javaFiles() throws IOException {
        try (var sourceFiles = Files.walk(SOURCE_ROOT)) {
            return sourceFiles.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        }
    }

    private String typePackage(String name, String source) {
        if (name.endsWith("Request.java") || name.endsWith("Response.java")) {
            return "dto";
        }
        if (name.equals("BankingRuleViolation.java")) {
            return "exception";
        }
        String declaredName = name.substring(0, name.length() - ".java".length());
        Pattern domainType = Pattern.compile("\\bpublic\\s+(?:record|enum)\\s+" + Pattern.quote(declaredName) + "\\b");
        if (domainType.matcher(source).find()) {
            return "model";
        }
        return TYPE_PACKAGES.entrySet().stream()
                .filter(entry -> name.endsWith(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Unclassified production class: " + name));
    }
}
