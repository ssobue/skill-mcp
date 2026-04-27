package dev.sobue.ai.skill.mcp.catalog;

import dev.sobue.ai.skill.mcp.config.SkillMcpProperties;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class GitHubSkillScanner {

  private static final Pattern HTTPS_REPOSITORY =
      Pattern.compile("^https://github\\.com/([^/]+)/([^/]+?)(?:\\.git)?/?$");
  private static final Pattern SSH_REPOSITORY =
      Pattern.compile("^git@github\\.com:([^/]+)/([^/]+?)(?:\\.git)?$");

  private final GitHubClient gitHubClient;
  private final SkillScanner skillScanner;

  public CatalogSnapshot scan(SkillMcpProperties.GitHub config) {
    List<SkillEntry> entries = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    Set<String> skillIds = new HashSet<>();

    for (SkillMcpProperties.Repository repository : config.repositories()) {
      parseRepository(repository)
          .ifPresentOrElse(
              ref -> scanRepository(ref, entries, warnings, skillIds),
              () -> warnings.add("GitHub repository URL is invalid: " + repository.url()));
    }

    entries.sort(Comparator.comparing(SkillEntry::skillId));
    return new CatalogSnapshot(List.copyOf(entries), List.copyOf(warnings), Instant.now());
  }

  private void scanRepository(
      GitHubRepositoryRef repository,
      List<SkillEntry> entries,
      List<String> warnings,
      Set<String> skillIds) {
    try {
      GitHubTree tree = gitHubClient.fetchTree(repository);
      if (tree.truncated()) {
        warnings.add(repository.url() + " tree response is truncated; some Skills may be missing");
      }
      Set<String> paths = tree.items().stream().map(GitHubTreeItem::path).collect(Collectors.toSet());
      List<GitHubTreeItem> manifests =
          tree.items().stream()
              .filter(item -> "blob".equals(item.type()))
              .filter(item -> item.path().endsWith("/skill.yaml") || item.path().equals("skill.yaml"))
              .sorted(Comparator.comparing(GitHubTreeItem::path))
              .toList();
      for (GitHubTreeItem manifestItem : manifests) {
        readManifest(repository, manifestItem, paths, entries, warnings, skillIds);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      warnings.add("Interrupted while scanning GitHub repository " + repository.url() + ": " + e.getMessage());
    } catch (Exception e) {
      warnings.add("Failed to scan GitHub repository " + repository.url() + ": " + e.getMessage());
    }
  }

  private void readManifest(
      GitHubRepositoryRef repository,
      GitHubTreeItem manifestItem,
      Set<String> paths,
      List<SkillEntry> entries,
      List<String> warnings,
      Set<String> skillIds) {
    try {
      String yaml = gitHubClient.fetchBlobText(repository, manifestItem.sha());
      SkillManifest manifest = skillScanner.readSkillManifest(yaml);
      List<String> validationErrors = skillScanner.validate(manifest);
      String source = repository.url() + "/" + manifestItem.path();
      if (!validationErrors.isEmpty()) {
        warnings.add(source + " is invalid: " + String.join(", ", validationErrors));
        return;
      }
      if (!skillIds.add(manifest.skillId())) {
        warnings.add(source + " skipped because skill_id is duplicated: " + manifest.skillId());
        return;
      }
      String skillMarkdownPath = normalizePath(manifest.skillPath() + "/SKILL.md");
      if (!paths.contains(skillMarkdownPath)) {
        warnings.add(source + " skipped because SKILL.md is missing at " + skillMarkdownPath);
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
              repository.url(),
              repository.ref(),
              manifest.skillPath(),
              Path.of("github", repository.owner(), repository.name()),
              Path.of(manifestItem.path()),
              Path.of(manifest.skillPath()),
              Instant.now()));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      warnings.add(
          "Interrupted while reading GitHub Skill manifest "
              + repository.url()
              + "/"
              + manifestItem.path()
              + ": "
              + e.getMessage());
    } catch (Exception e) {
      warnings.add(
          "Failed to read GitHub Skill manifest "
              + repository.url()
              + "/"
              + manifestItem.path()
              + ": "
              + e.getMessage());
    }
  }

  private Optional<GitHubRepositoryRef> parseRepository(SkillMcpProperties.Repository repository) {
    if (!StringUtils.hasText(repository.url())) {
      return Optional.empty();
    }
    Matcher https = HTTPS_REPOSITORY.matcher(repository.url());
    if (https.matches()) {
      return Optional.of(
          new GitHubRepositoryRef(https.group(1), https.group(2), repository.url(), repository.ref()));
    }
    Matcher ssh = SSH_REPOSITORY.matcher(repository.url());
    if (ssh.matches()) {
      return Optional.of(
          new GitHubRepositoryRef(ssh.group(1), ssh.group(2), repository.url(), repository.ref()));
    }
    return Optional.empty();
  }

  private String normalizePath(String path) {
    return path.replaceAll("^/+", "").replaceAll("/{2,}", "/");
  }

  private List<String> nullToList(@Nullable List<String> values) {
    return values == null ? List.of() : List.copyOf(values);
  }
}
