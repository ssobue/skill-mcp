package dev.sobue.ai.skill.mcp.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import dev.sobue.ai.skill.mcp.catalog.CatalogSnapshot;
import dev.sobue.ai.skill.mcp.catalog.SkillCatalogService;
import dev.sobue.ai.skill.mcp.catalog.SkillEntry;
import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tools.jackson.databind.json.JsonMapper;

class SkillMcpServerTests {

  private final SkillCatalogService catalogService = Mockito.mock(SkillCatalogService.class);

  private SkillMcpServer server;

  @BeforeEach
  void setUp() {
    server = new SkillMcpServer(catalogService, JsonMapper.builder().findAndAddModules().build());
  }

  @Test
  void searchesSkills() {
    when(catalogService.search("example", "java", "Engineering", "Platform")).thenReturn(List.of(skill()));

    SkillMcpServer.SkillSearchResult result =
        server.searchSkills("example", "java", "Engineering", "Platform");

    assertThat(result.skills()).hasSize(1);
    assertThat(result.skills().getFirst().skillId()).isEqualTo("example-skill");
    assertThat(result.skills().getFirst().location().repositoryUrl()).isEqualTo("https://example.com/repo.git");
  }

  @Test
  void returnsSkillLocation() {
    when(catalogService.findById("example-skill")).thenReturn(Optional.of(skill()));

    SkillMcpServer.SkillLocationView location = server.getSkillLocation("example-skill");

    assertThat(location.repositoryUrl()).isEqualTo("https://example.com/repo.git");
    assertThat(location.ref()).isEqualTo("main");
  }

  @Test
  void failsWhenSkillLocationIsMissing() {
    when(catalogService.findById("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> server.getSkillLocation("missing"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Skill not found: missing");
  }

  @Test
  void readsSkillResource() {
    when(catalogService.findById("example-skill")).thenReturn(Optional.of(skill()));

    ReadResourceResult result = server.readSkillResource("example-skill");

    assertThat(result.contents()).hasSize(1);
    assertThat(result.contents().getFirst().uri()).isEqualTo("skill://example-skill");
    assertThat(result.contents().getFirst().mimeType()).isEqualTo("application/json");
    assertThat((TextResourceContents) result.contents().getFirst()).extracting(TextResourceContents::text)
        .asString()
        .contains("Example Skill");
  }

  @Test
  void readsCatalogSummaryResource() {
    when(catalogService.snapshot()).thenReturn(new CatalogSnapshot(List.of(skill()), List.of(), Instant.now()));

    ReadResourceResult result = server.readSkillCatalogSummary();

    assertThat(result.contents()).hasSize(1);
    assertThat(result.contents().getFirst().uri()).isEqualTo("skill-catalog://summary");
    assertThat((TextResourceContents) result.contents().getFirst()).extracting(TextResourceContents::text)
        .asString()
        .contains("example-skill");
  }

  @Test
  void createsFindSkillPrompt() {
    GetPromptResult result = server.findSkillPrompt("generate an API");

    assertThat(result.description()).isEqualTo("Find a Skill for a user task.");
    assertThat(result.messages()).hasSize(1);
    assertThat(result.messages().getFirst().content().type()).isEqualTo("text");
    assertThat((TextContent) result.messages().getFirst().content()).extracting(TextContent::text)
        .asString()
        .contains("generate an API");
  }

  @Test
  void exposesSpringAiMcpAnnotations() throws NoSuchMethodException {
    assertThat(
            SkillMcpServer.class
                .getMethod("searchSkills", String.class, String.class, String.class, String.class)
                .isAnnotationPresent(org.springframework.ai.mcp.annotation.McpTool.class))
        .isTrue();
    assertThat(
            SkillMcpServer.class
                .getMethod("readSkillResource", String.class)
                .isAnnotationPresent(org.springframework.ai.mcp.annotation.McpResource.class))
        .isTrue();
    assertThat(
            SkillMcpServer.class
                .getMethod("findSkillPrompt", String.class)
                .isAnnotationPresent(org.springframework.ai.mcp.annotation.McpPrompt.class))
        .isTrue();
  }

  @Test
  void keepsExistingSnapshotCatalogAvailableForSpringAiServer() {
    when(catalogService.snapshot()).thenReturn(new CatalogSnapshot(List.of(skill()), List.of(), Instant.now()));

    assertThat(catalogService.snapshot().skills()).hasSize(1);
  }

  private SkillEntry skill() {
    return new SkillEntry(
        "example-skill",
        "Example Skill",
        "Example description",
        "Engineering",
        "Platform",
        List.of("example"),
        List.of(),
        null,
        "https://example.com/repo.git",
        "main",
        "skills/example-skill",
        Path.of("."),
        Path.of("skills/example-skill/skill.yaml"),
        Path.of("skills/example-skill"),
        Instant.now());
  }
}
