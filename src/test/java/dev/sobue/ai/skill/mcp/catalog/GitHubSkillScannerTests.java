package dev.sobue.ai.skill.mcp.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sobue.ai.skill.mcp.config.SkillMcpProperties;
import java.io.IOException;
import java.util.List;
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
                """
                skill_id: example-skill
                name: Example Skill
                description: Helps test GitHub catalog scanning.
                owner_department: Engineering
                owner_team: Platform
                tags:
                  - test
                skill_path: skills/example-skill
                """),
            new SkillScanner());

    CatalogSnapshot snapshot = scanner.scan(config);

    assertThat(snapshot.warnings()).isEmpty();
    assertThat(snapshot.skills()).hasSize(1);
    SkillEntry skill = snapshot.skills().getFirst();
    assertThat(skill.skillId()).isEqualTo("example-skill");
    assertThat(skill.repositoryUrl()).isEqualTo("https://github.com/example/skills.git");
    assertThat(skill.ref()).isEqualTo("main");
    assertThat(skill.manifestPath().toString()).isEqualTo("skills/example-skill/skill.yaml");
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
                """
                skill_id: broken-skill
                name: Broken Skill
                description: Missing SKILL.md.
                owner_department: Engineering
                owner_team: Platform
                tags:
                  - test
                skill_path: skills/broken-skill
                """),
            new SkillScanner());

    CatalogSnapshot snapshot = scanner.scan(config);

    assertThat(snapshot.skills()).isEmpty();
    assertThat(snapshot.warnings()).anyMatch(warning -> warning.contains("SKILL.md is missing"));
  }

  private record FakeGitHubClient(GitHubTree tree, String manifestText) implements GitHubClient {

    @Override
    public GitHubTree fetchTree(GitHubRepositoryRef repository) {
      return tree;
    }

    @Override
    public String fetchBlobText(GitHubRepositoryRef repository, String sha) throws IOException {
      if (!"manifest-sha".equals(sha)) {
        throw new IOException("Unexpected sha: " + sha);
      }
      return manifestText;
    }
  }
}
