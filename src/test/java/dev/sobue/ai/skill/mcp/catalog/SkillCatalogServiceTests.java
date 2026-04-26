package dev.sobue.ai.skill.mcp.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.sobue.ai.skill.mcp.config.SkillMcpProperties;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class SkillCatalogServiceTests {

  @Test
  void refreshMergesSourcesAndSkipsDuplicateSkillIds() {
    SkillScanner scanner = mock(SkillScanner.class);
    GitHubSkillScanner gitHubSkillScanner = mock(GitHubSkillScanner.class);
    SkillMcpProperties properties = new SkillMcpProperties();
    SkillEntry localSkill = skill("example-skill", "java", "Engineering", "Platform");
    SkillEntry duplicateGitHubSkill = skill("example-skill", "spring", "Engineering", "API");
    SkillEntry gitHubSkill = skill("github-skill", "docs", "Enablement", "Docs");
    when(scanner.scan(properties.scan().roots()))
        .thenReturn(new CatalogSnapshot(List.of(localSkill), List.of("local warning"), Instant.now()));
    when(gitHubSkillScanner.scan(properties.github()))
        .thenReturn(new CatalogSnapshot(List.of(duplicateGitHubSkill, gitHubSkill), List.of("github warning"), Instant.now()));

    SkillCatalogService service = new SkillCatalogService(properties, scanner, gitHubSkillScanner);
    service.refresh();

    CatalogSnapshot snapshot = service.snapshot();
    assertThat(snapshot.skills()).extracting(SkillEntry::skillId).containsExactly("example-skill", "github-skill");
    assertThat(snapshot.warnings())
        .contains("local warning", "github warning")
        .anyMatch(warning -> warning.contains("duplicated across sources"));
    assertThat(service.findById("github-skill")).contains(gitHubSkill);
  }

  @Test
  void searchFiltersByQueryTagDepartmentAndTeam() {
    SkillScanner scanner = mock(SkillScanner.class);
    GitHubSkillScanner gitHubSkillScanner = mock(GitHubSkillScanner.class);
    SkillMcpProperties properties = new SkillMcpProperties();
    SkillEntry matchingSkill = skill("spring-api", "java", "Engineering", "Platform");
    SkillEntry otherSkill = skill("docs-helper", "docs", "Enablement", "Docs");
    when(scanner.scan(properties.scan().roots()))
        .thenReturn(new CatalogSnapshot(List.of(matchingSkill, otherSkill), List.of(), Instant.now()));
    when(gitHubSkillScanner.scan(properties.github()))
        .thenReturn(new CatalogSnapshot(List.of(), List.of(), Instant.now()));

    SkillCatalogService service = new SkillCatalogService(properties, scanner, gitHubSkillScanner);
    service.refresh();

    assertThat(service.search("spring", "java", "engineer", "plat"))
        .containsExactly(matchingSkill);
    assertThat(service.search("spring", "docs", null, null)).isEmpty();
    assertThat(service.findById("missing")).isEmpty();
  }

  private SkillEntry skill(String skillId, String tag, String ownerDepartment, String ownerTeam) {
    return new SkillEntry(
        skillId,
        skillId + " name",
        "Description for " + skillId,
        ownerDepartment,
        ownerTeam,
        List.of(tag),
        List.of(),
        null,
        "https://example.com/repo.git",
        "main",
        "skills/" + skillId,
        Path.of("."),
        Path.of("skills/" + skillId + "/skill.yaml"),
        Path.of("skills/" + skillId),
        Instant.now());
  }
}
