package dev.sobue.ai.skill.mcp.catalog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.yaml.snakeyaml.Yaml;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class SkillScanner {

  private final Yaml yaml = new Yaml();

  public CatalogSnapshot scan(List<Path> roots) {
    List<SkillEntry> entries = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    Set<String> skillIds = new HashSet<>();

    for (Path root : roots) {
      Path normalizedRoot = root.toAbsolutePath().normalize();
      if (!Files.exists(normalizedRoot)) {
        warnings.add("Scan root does not exist: " + normalizedRoot);
        continue;
      }
      scanRoot(normalizedRoot, entries, warnings, skillIds);
    }

    entries.sort(Comparator.comparing(SkillEntry::skillId));
    return new CatalogSnapshot(List.copyOf(entries), List.copyOf(warnings), Instant.now());
  }

  private void scanRoot(
      Path root, List<SkillEntry> entries, List<String> warnings, Set<String> skillIds) {
    try (Stream<Path> stream = Files.walk(root)) {
      stream
          .filter(path -> path.getFileName().toString().equals("skill.yaml"))
          .sorted()
          .forEach(path -> readManifest(root, path, entries, warnings, skillIds));
    } catch (IOException e) {
      warnings.add("Failed to scan root " + root + ": " + e.getMessage());
    }
  }

  private void readManifest(
      Path scanRoot,
      Path manifestPath,
      List<SkillEntry> entries,
      List<String> warnings,
      Set<String> skillIds) {
    try {
      SkillManifest manifest = readSkillManifest(manifestPath);
      List<String> validationErrors = validate(manifest);
      if (!validationErrors.isEmpty()) {
        warnings.add(manifestPath + " is invalid: " + String.join(", ", validationErrors));
        return;
      }

      if (!skillIds.add(manifest.skillId())) {
        warnings.add(manifestPath + " skipped because skill_id is duplicated: " + manifest.skillId());
        return;
      }

      Path repositoryRoot = findRepositoryRoot(manifestPath).orElse(scanRoot);
      Path skillDirectory = repositoryRoot.resolve(manifest.skillPath()).normalize();
      Path skillFile = skillDirectory.resolve("SKILL.md");
      if (!Files.isRegularFile(skillFile)) {
        warnings.add(manifestPath + " skipped because SKILL.md is missing at " + skillFile);
        return;
      }

      entries.add(
          new SkillEntry(
              manifest.skillId(),
              manifest.name(),
              manifest.description(),
              manifest.ownerDepartment(),
              manifest.ownerTeam(),
              List.copyOf(manifest.tags()),
              nullToList(manifest.visibilityGroups()),
              manifest.executable(),
              gitConfig(repositoryRoot, "remote.origin.url").orElse(repositoryRoot.toString()),
              gitConfig(repositoryRoot, "branch." + currentBranch(repositoryRoot).orElse("HEAD") + ".merge")
                  .orElse(currentBranch(repositoryRoot).orElse("HEAD")),
              manifest.skillPath(),
              repositoryRoot,
              manifestPath,
              skillDirectory,
              Instant.now()));
    } catch (Exception e) {
      warnings.add("Failed to read " + manifestPath + ": " + e.getMessage());
    }
  }

  @SuppressWarnings("unchecked")
  SkillManifest readSkillManifest(Path manifestPath) throws IOException {
    Object loaded;
    try (var reader = Files.newBufferedReader(manifestPath, StandardCharsets.UTF_8)) {
      loaded = yaml.load(reader);
    }
    return readSkillManifest(loaded);
  }

  SkillManifest readSkillManifest(String manifestYaml) {
    Object loaded = yaml.load(manifestYaml);
    return readSkillManifest(loaded);
  }

  @SuppressWarnings("unchecked")
  private SkillManifest readSkillManifest(Object loaded) {
    Map<String, Object> values =
        loaded instanceof Map<?, ?> map ? (Map<String, Object>) map : new LinkedHashMap<>();
    return new SkillManifest(
        stringValue(values, "skill_id"),
        stringValue(values, "name"),
        stringValue(values, "description"),
        stringValue(values, "owner_department"),
        stringValue(values, "owner_team"),
        stringList(values.get("tags")),
        stringValue(values, "skill_path"),
        stringList(values.get("visibility_groups")),
        executable(values.get("executable")));
  }

  @SuppressWarnings("unchecked")
  private ExecutableDefinition executable(Object value) {
    if (!(value instanceof Map<?, ?> map)) {
      return null;
    }
    Map<String, Object> values = (Map<String, Object>) map;
    return new ExecutableDefinition(
        stringValue(values, "type"),
        stringValue(values, "runtime"),
        stringValue(values, "working_directory"),
        stringValue(values, "command"),
        stringList(values.get("args")),
        mapList(values.get("inputs")),
        mapValue(values.get("permissions")));
  }

  private String stringValue(Map<String, Object> values, String key) {
    Object value = values.get(key);
    return value == null ? null : value.toString();
  }

  private List<String> stringList(Object value) {
    if (value instanceof List<?> list) {
      return list.stream().map(Object::toString).toList();
    }
    if (value == null) {
      return List.of();
    }
    return List.of(value.toString());
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> mapList(Object value) {
    if (value instanceof List<?> list) {
      return list.stream()
          .filter(Map.class::isInstance)
          .map(item -> (Map<String, Object>) item)
          .toList();
    }
    return List.of();
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> mapValue(Object value) {
    return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
  }

  List<String> validate(SkillManifest manifest) {
    List<String> errors = new ArrayList<>();
    require(errors, manifest.skillId(), "skill_id");
    require(errors, manifest.name(), "name");
    require(errors, manifest.description(), "description");
    require(errors, manifest.ownerDepartment(), "owner_department");
    require(errors, manifest.ownerTeam(), "owner_team");
    require(errors, manifest.skillPath(), "skill_path");
    if (manifest.tags() == null || manifest.tags().isEmpty()) {
      errors.add("tags is required");
    }
    return errors;
  }

  private void require(List<String> errors, String value, String field) {
    if (!StringUtils.hasText(value)) {
      errors.add(field + " is required");
    }
  }

  private Optional<Path> findRepositoryRoot(Path path) {
    Path current = Files.isDirectory(path) ? path : path.getParent();
    while (current != null) {
      if (Files.isDirectory(current.resolve(".git"))) {
        return Optional.of(current);
      }
      current = current.getParent();
    }
    return Optional.empty();
  }

  private Optional<String> currentBranch(Path repositoryRoot) {
    Path head = repositoryRoot.resolve(".git/HEAD");
    if (!Files.isRegularFile(head)) {
      return Optional.empty();
    }
    try {
      String content = Files.readString(head, StandardCharsets.UTF_8).trim();
      if (content.startsWith("ref: refs/heads/")) {
        return Optional.of(content.substring("ref: refs/heads/".length()));
      }
      return Optional.of("HEAD");
    } catch (IOException e) {
      return Optional.empty();
    }
  }

  private Optional<String> gitConfig(Path repositoryRoot, String key) {
    Path config = repositoryRoot.resolve(".git/config");
    if (!Files.isRegularFile(config)) {
      return Optional.empty();
    }
    try {
      List<String> lines = Files.readAllLines(config, StandardCharsets.UTF_8);
      for (String line : lines) {
        String trimmed = line.trim();
        if (trimmed.startsWith(key + " = ")) {
          return Optional.of(trimmed.substring((key + " = ").length()));
        }
        if (trimmed.startsWith("url = ") && key.equals("remote.origin.url")) {
          return Optional.of(trimmed.substring("url = ".length()));
        }
      }
    } catch (IOException e) {
      return Optional.empty();
    }
    return Optional.empty();
  }

  private List<String> nullToList(List<String> values) {
    return values == null ? List.of() : List.copyOf(values);
  }
}
