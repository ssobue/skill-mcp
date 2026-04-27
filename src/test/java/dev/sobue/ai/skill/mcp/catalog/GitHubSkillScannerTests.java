package dev.sobue.ai.skill.mcp.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sobue.ai.skill.mcp.config.SkillMcpProperties;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class GitHubSkillScannerTests {

  @Test
  void scansGitHubRepositoryManifest() {
    SkillMcpProperties.GitHub config =
        new SkillMcpProperties.GitHub(
            "https://api.github.com",
            null,
            null,
            null,
            null,
            List.of(new SkillMcpProperties.Repository("https://github.com/example/skills.git", "main")));

    GitHubSkillScanner scanner =
        new GitHubSkillScanner(
            new FakeGitHubClient(
                new GitHubTree(
                    List.of(
                        new GitHubTreeItem("skills/example-skill/skill.yaml", "blob", "manifest-sha"),
                        new GitHubTreeItem("skills/example-skill/SKILL.md", "blob", "skill-sha")),
                    false),
                Map.of("manifest-sha", validManifest("example-skill", "skills/example-skill"))),
            new SkillScanner());

    CatalogSnapshot snapshot = scanner.scan(config);

    assertThat(snapshot.warnings()).isEmpty();
    assertThat(snapshot.skills()).hasSize(1);
    SkillEntry skill = snapshot.skills().getFirst();
    assertThat(skill.skillId()).isEqualTo("example-skill");
    assertThat(skill.repositoryUrl()).isEqualTo("https://github.com/example/skills.git");
    assertThat(skill.ref()).isEqualTo("main");
    assertThat(skill.manifestPath()).hasToString("skills/example-skill/skill.yaml");
  }

  @Test
  void skipsGitHubManifestWhenSkillMarkdownIsMissing() {
    SkillMcpProperties.GitHub config =
        new SkillMcpProperties.GitHub(
            "https://api.github.com",
            null,
            null,
            null,
            null,
            List.of(new SkillMcpProperties.Repository("git@github.com:example/skills.git")));

    GitHubSkillScanner scanner =
        new GitHubSkillScanner(
            new FakeGitHubClient(
                new GitHubTree(
                    List.of(new GitHubTreeItem("skills/broken-skill/skill.yaml", "blob", "manifest-sha")),
                    false),
                Map.of("manifest-sha", validManifest("broken-skill", "skills/broken-skill"))),
            new SkillScanner());

    CatalogSnapshot snapshot = scanner.scan(config);

    assertThat(snapshot.skills()).isEmpty();
    assertThat(snapshot.warnings()).anyMatch(warning -> warning.contains("SKILL.md is missing"));
  }

  @Test
  void reportsInvalidGitHubRepositoryUrls() {
    SkillMcpProperties.GitHub config =
        new SkillMcpProperties.GitHub(
            "https://api.github.com",
            null,
            null,
            null,
            null,
            List.of(
                new SkillMcpProperties.Repository("https://example.com/not-github.git"),
                new SkillMcpProperties.Repository(null, "main")));

    GitHubSkillScanner scanner =
        new GitHubSkillScanner(new FakeGitHubClient(new GitHubTree(List.of(), false), Map.of()), new SkillScanner());

    CatalogSnapshot snapshot = scanner.scan(config);

    assertThat(snapshot.skills()).isEmpty();
    assertThat(snapshot.warnings())
        .containsExactly(
            "GitHub repository URL is invalid: https://example.com/not-github.git",
            "GitHub repository URL is invalid: null");
  }

  @Test
  void reportsTruncatedGitHubTreeResponse() {
    SkillMcpProperties.GitHub config =
        new SkillMcpProperties.GitHub(
            "https://api.github.com",
            null,
            null,
            null,
            null,
            List.of(new SkillMcpProperties.Repository("https://github.com/example/skills.git", "main")));

    GitHubSkillScanner scanner =
        new GitHubSkillScanner(
            new FakeGitHubClient(
                new GitHubTree(
                    List.of(
                        new GitHubTreeItem("skills/example-skill/skill.yaml", "blob", "manifest-sha"),
                        new GitHubTreeItem("skills/example-skill/SKILL.md", "blob", "skill-sha")),
                    true),
                Map.of("manifest-sha", validManifest("example-skill", "skills/example-skill"))),
            new SkillScanner());

    CatalogSnapshot snapshot = scanner.scan(config);

    assertThat(snapshot.skills()).hasSize(1);
    assertThat(snapshot.warnings())
        .contains("https://github.com/example/skills.git tree response is truncated; some Skills may be missing");
  }

  @Test
  void skipsDuplicatedGitHubSkillIds() {
    SkillMcpProperties.GitHub config =
        new SkillMcpProperties.GitHub(
            "https://api.github.com",
            null,
            null,
            null,
            null,
            List.of(new SkillMcpProperties.Repository("https://github.com/example/skills.git", "main")));

    GitHubSkillScanner scanner =
        new GitHubSkillScanner(
            new FakeGitHubClient(
                new GitHubTree(
                    List.of(
                        new GitHubTreeItem("skills/first/skill.yaml", "blob", "first-sha"),
                        new GitHubTreeItem("skills/first/SKILL.md", "blob", "first-md-sha"),
                        new GitHubTreeItem("skills/second/skill.yaml", "blob", "second-sha"),
                        new GitHubTreeItem("skills/second/SKILL.md", "blob", "second-md-sha")),
                    false),
                Map.of(
                    "first-sha",
                    validManifest("example-skill", "skills/first"),
                    "second-sha",
                    validManifest("example-skill", "skills/second"))),
            new SkillScanner());

    CatalogSnapshot snapshot = scanner.scan(config);

    assertThat(snapshot.skills()).hasSize(1);
    assertThat(snapshot.warnings()).anyMatch(warning -> warning.contains("skill_id is duplicated: example-skill"));
  }

  @Test
  void skipsInvalidGitHubManifest() {
    SkillMcpProperties.GitHub config =
        new SkillMcpProperties.GitHub(
            "https://api.github.com",
            null,
            null,
            null,
            null,
            List.of(new SkillMcpProperties.Repository("https://github.com/example/skills.git", "main")));

    GitHubSkillScanner scanner =
        new GitHubSkillScanner(
            new FakeGitHubClient(
                new GitHubTree(
                    List.of(
                        new GitHubTreeItem("skills/broken/skill.yaml", "blob", "manifest-sha"),
                        new GitHubTreeItem("skills/broken/SKILL.md", "blob", "skill-sha")),
                    false),
                Map.of(
                    "manifest-sha",
                    """
                    skill_id: broken-skill
                    name: Broken Skill
                    """)),
            new SkillScanner());

    CatalogSnapshot snapshot = scanner.scan(config);

    assertThat(snapshot.skills()).isEmpty();
    assertThat(snapshot.warnings()).anyMatch(warning -> warning.contains("is invalid"));
  }

  @Test
  void reportsGitHubTreeFetchFailures() {
    SkillMcpProperties.GitHub config =
        new SkillMcpProperties.GitHub(
            "https://api.github.com",
            null,
            null,
            null,
            null,
            List.of(new SkillMcpProperties.Repository("https://github.com/example/skills.git", "main")));

    GitHubSkillScanner scanner =
        new GitHubSkillScanner(
            new FakeGitHubClient(new IOException("tree failed")), new SkillScanner());

    CatalogSnapshot snapshot = scanner.scan(config);

    assertThat(snapshot.skills()).isEmpty();
    assertThat(snapshot.warnings())
        .contains("Failed to scan GitHub repository https://github.com/example/skills.git: tree failed");
  }

  @Test
  void reportsGitHubManifestFetchFailures() {
    SkillMcpProperties.GitHub config =
        new SkillMcpProperties.GitHub(
            "https://api.github.com",
            null,
            null,
            null,
            null,
            List.of(new SkillMcpProperties.Repository("https://github.com/example/skills.git", "main")));

    GitHubSkillScanner scanner =
        new GitHubSkillScanner(
            new FakeGitHubClient(
                new GitHubTree(
                    List.of(
                        new GitHubTreeItem("skills/example-skill/skill.yaml", "blob", "manifest-sha"),
                        new GitHubTreeItem("skills/example-skill/SKILL.md", "blob", "skill-sha")),
                    false),
                Map.of(),
                new IOException("blob failed")),
            new SkillScanner());

    CatalogSnapshot snapshot = scanner.scan(config);

    assertThat(snapshot.skills()).isEmpty();
    assertThat(snapshot.warnings())
        .contains(
            "Failed to read GitHub Skill manifest https://github.com/example/skills.git/skills/example-skill/skill.yaml: blob failed");
  }

  private static String validManifest(String skillId, String skillPath) {
    return """
        skill_id: %s
        name: Example Skill
        description: Helps test GitHub catalog scanning.
        owner_department: Engineering
        owner_team: Platform
        tags:
          - test
        skill_path: %s
        """
        .formatted(skillId, skillPath);
  }

  private record FakeGitHubClient(
      @Nullable GitHubTree tree,
      Map<String, String> manifestTexts,
      @Nullable IOException treeException,
      @Nullable IOException blobException)
      implements GitHubClient {

    FakeGitHubClient(GitHubTree tree, Map<String, String> manifestTexts) {
      this(tree, manifestTexts, null, null);
    }

    FakeGitHubClient(IOException treeException) {
      this(null, Map.of(), treeException, null);
    }

    FakeGitHubClient(GitHubTree tree, Map<String, String> manifestTexts, IOException blobException) {
      this(tree, manifestTexts, null, blobException);
    }

    @Override
    public GitHubTree fetchTree(GitHubRepositoryRef repository) throws IOException {
      if (treeException != null) {
        throw treeException;
      }
      if (tree == null) {
        throw new IOException("GitHub tree is not configured");
      }
      return tree;
    }

    @Override
    public String fetchBlobText(GitHubRepositoryRef repository, String sha) throws IOException {
      if (blobException != null) {
        throw blobException;
      }
      String manifestText = manifestTexts.get(sha);
      if (manifestText == null) {
        throw new IOException("Unexpected sha: " + sha);
      }
      return manifestText;
    }
  }
}
