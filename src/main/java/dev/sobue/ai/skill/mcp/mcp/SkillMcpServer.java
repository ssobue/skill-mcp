package dev.sobue.ai.skill.mcp.mcp;

import dev.sobue.ai.skill.mcp.catalog.ExecutableDefinition;
import dev.sobue.ai.skill.mcp.catalog.SkillCatalogService;
import dev.sobue.ai.skill.mcp.catalog.SkillEntry;
import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.PromptMessage;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.spec.McpSchema.Role;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
public class SkillMcpServer {

  private static final String MEDIA_TYPE_JSON = "application/json";
  private static final String SKILL_URI_PREFIX = "skill://";

  private final SkillCatalogService catalogService;
  private final JsonMapper objectMapper;

  @McpTool(name = "search_skills", description = "Search discovered Skills by keyword, tag, department, or team.")
  public SkillSearchResult searchSkills(
      @McpToolParam(description = "Keyword to match against Skill id, name, description, or tags.", required = false)
          @Nullable
          String query,
      @McpToolParam(description = "Tag that the Skill must have.", required = false) @Nullable String tag,
      @McpToolParam(description = "Owner department filter.", required = false) @Nullable String ownerDepartment,
      @McpToolParam(description = "Owner team filter.", required = false) @Nullable String ownerTeam) {
    return new SkillSearchResult(
        catalogService.search(query, tag, ownerDepartment, ownerTeam).stream().map(this::skillView).toList());
  }

  @McpTool(name = "get_skill_location", description = "Return Git repository location details for a Skill.")
  public SkillLocationView getSkillLocation(
      @McpToolParam(description = "Skill id to locate.", required = true) String skillId) {
    return catalogService
        .findById(skillId)
        .map(this::locationView)
        .orElseThrow(() -> new IllegalArgumentException("Skill not found: " + skillId));
  }

  @McpResource(
      uri = "skill-catalog://summary",
      name = "skill-catalog",
      title = "Skill catalog summary",
      description = "Read a summary of all discovered Skills.",
      mimeType = MEDIA_TYPE_JSON)
  public ReadResourceResult readSkillCatalogSummary() {
    SkillSearchResult summary =
        new SkillSearchResult(catalogService.snapshot().skills().stream().map(this::skillView).toList());
    return new ReadResourceResult(
        List.of(new TextResourceContents("skill-catalog://summary", MEDIA_TYPE_JSON, toJson(summary))));
  }

  @McpResource(
      uri = "skill://{skill_id}",
      name = "skill",
      title = "Skill metadata",
      description = "Read metadata and location for a discovered Skill.",
      mimeType = MEDIA_TYPE_JSON)
  public ReadResourceResult readSkillResource(
      @McpArg(name = "skill_id", description = "Skill id to read.", required = true) String skillId) {
    SkillEntry skill =
        catalogService
            .findById(skillId)
            .orElseThrow(() -> new IllegalArgumentException("Skill not found: " + skillId));
    return new ReadResourceResult(
        List.of(new TextResourceContents(skillUri(skill.skillId()), MEDIA_TYPE_JSON, toJson(skillView(skill)))));
  }

  @McpPrompt(
      name = "find-skill",
      title = "Find a Skill",
      description = "Help a user find a suitable Skill from the central catalog.")
  public GetPromptResult findSkillPrompt(
      @McpArg(name = "task", description = "User task", required = true) String task) {
    return new GetPromptResult(
        "Find a Skill for a user task.",
        List.of(
            new PromptMessage(
                Role.USER,
                new TextContent(
                    "Find the best Skill for this task using search_skills, then explain how to fetch it: "
                        + task))));
  }

  private SkillView skillView(SkillEntry skill) {
    return new SkillView(
        skill.skillId(),
        skill.name(),
        skill.description(),
        skill.ownerDepartment(),
        skill.ownerTeam(),
        skill.tags(),
        skill.visibilityGroups(),
        locationView(skill),
        skill.executable());
  }

  private SkillLocationView locationView(SkillEntry skill) {
    return new SkillLocationView(
        skill.repositoryUrl(), skill.ref(), skill.skillPath(), skill.manifestPath().toString());
  }

  private String skillUri(String skillId) {
    return SKILL_URI_PREFIX + skillId;
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
    } catch (JacksonException e) {
      throw new IllegalStateException("Failed to serialize MCP payload", e);
    }
  }

  public record SkillSearchResult(List<SkillView> skills) {}

  public record SkillView(
      String skillId,
      String name,
      String description,
      String ownerDepartment,
      String ownerTeam,
      List<String> tags,
      List<String> visibilityGroups,
      SkillLocationView location,
      @Nullable ExecutableDefinition executable) {}

  public record SkillLocationView(String repositoryUrl, String ref, String skillPath, String manifestPath) {}
}
