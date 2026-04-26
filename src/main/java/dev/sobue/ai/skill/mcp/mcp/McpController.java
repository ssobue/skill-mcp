package dev.sobue.ai.skill.mcp.mcp;

import dev.sobue.ai.skill.mcp.catalog.SkillCatalogService;
import dev.sobue.ai.skill.mcp.catalog.SkillEntry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@RestController
@RequiredArgsConstructor
public class McpController {

  private static final String PROTOCOL_VERSION = "2025-11-25";

  private final SkillCatalogService catalogService;
  private final JsonMapper objectMapper;

  @PostMapping("/mcp")
  public ResponseEntity<JsonRpcResponse> handle(@RequestBody JsonRpcRequest request) {
    if (request.id() == null) {
      return ResponseEntity.noContent().build();
    }
    return ResponseEntity.ok(dispatch(request));
  }

  private JsonRpcResponse dispatch(JsonRpcRequest request) {
    return switch (request.method()) {
      case "initialize" -> JsonRpcResponse.result(request.id(), initializeResult());
      case "resources/list" -> JsonRpcResponse.result(request.id(), listResources());
      case "resources/read" -> readResource(request);
      case "resources/templates/list" -> JsonRpcResponse.result(request.id(), resourceTemplates());
      case "tools/list" -> JsonRpcResponse.result(request.id(), listTools());
      case "tools/call" -> callTool(request);
      case "prompts/list" -> JsonRpcResponse.result(request.id(), listPrompts());
      case "prompts/get" -> getPrompt(request);
      default -> JsonRpcResponse.error(request.id(), -32601, "Method not found: " + request.method());
    };
  }

  private Map<String, Object> initializeResult() {
    return Map.of(
        "protocolVersion",
        PROTOCOL_VERSION,
        "capabilities",
        Map.of(
            "resources", Map.of("listChanged", false),
            "tools", Map.of("listChanged", false),
            "prompts", Map.of("listChanged", false)),
        "serverInfo",
        Map.of("name", "skill-mcp", "version", "0.0.1"));
  }

  private Map<String, Object> listResources() {
    List<Map<String, Object>> resources =
        catalogService.snapshot().skills().stream()
            .map(
                skill ->
                    Map.<String, Object>of(
                        "uri", "skill://" + skill.skillId(),
                        "name", skill.skillId(),
                        "title", skill.name(),
                        "description", skill.description(),
                        "mimeType", "application/json"))
            .toList();
    return Map.of("resources", resources);
  }

  private JsonRpcResponse readResource(JsonRpcRequest request) {
    String uri = stringParam(request, "uri");
    if (uri == null || !uri.startsWith("skill://")) {
      return JsonRpcResponse.error(request.id(), -32602, "resources/read requires uri skill://<skill_id>");
    }
    String skillId = uri.substring("skill://".length());
    return catalogService
        .findById(skillId)
        .map(skill -> JsonRpcResponse.result(request.id(), resourceContents(skill)))
        .orElseGet(() -> JsonRpcResponse.error(request.id(), -32004, "Skill not found: " + skillId));
  }

  private Map<String, Object> resourceContents(SkillEntry skill) {
    return Map.of(
        "contents",
        List.of(
            Map.of(
                "uri",
                "skill://" + skill.skillId(),
                "mimeType",
                "application/json",
                "text",
                toJson(skillView(skill)))));
  }

  private Map<String, Object> resourceTemplates() {
    return Map.of(
        "resourceTemplates",
        List.of(
            Map.of(
                "uriTemplate",
                "skill://{skill_id}",
                "name",
                "skill",
                "title",
                "Skill metadata",
                "description",
                "Read metadata and location for a discovered Skill.",
                "mimeType",
                "application/json")));
  }

  private Map<String, Object> listTools() {
    return Map.of(
        "tools",
        List.of(
            Map.of(
                "name",
                "search_skills",
                "description",
                "Search discovered Skills by keyword, tag, department, or team.",
                "inputSchema",
                objectSchema(
                    Map.of(
                        "query", Map.of("type", "string"),
                        "tag", Map.of("type", "string"),
                        "owner_department", Map.of("type", "string"),
                        "owner_team", Map.of("type", "string")))),
            Map.of(
                "name",
                "get_skill_location",
                "description",
                "Return Git repository location details for a Skill.",
                "inputSchema",
                objectSchema(Map.of("skill_id", Map.of("type", "string")), List.of("skill_id")))));
  }

  private JsonRpcResponse callTool(JsonRpcRequest request) {
    String name = stringParam(request, "name");
    Map<String, Object> arguments = mapParam(request, "arguments");
    if ("search_skills".equals(name)) {
      List<Map<String, Object>> skills =
          catalogService
              .search(
                  stringValue(arguments, "query"),
                  stringValue(arguments, "tag"),
                  stringValue(arguments, "owner_department"),
                  stringValue(arguments, "owner_team"))
              .stream()
              .map(this::skillView)
              .toList();
      return JsonRpcResponse.result(request.id(), textToolResult(toJson(Map.of("skills", skills))));
    }
    if ("get_skill_location".equals(name)) {
      String skillId = stringValue(arguments, "skill_id");
      return catalogService
          .findById(skillId)
          .map(skill -> JsonRpcResponse.result(request.id(), textToolResult(toJson(locationView(skill)))))
          .orElseGet(() -> JsonRpcResponse.error(request.id(), -32004, "Skill not found: " + skillId));
    }
    return JsonRpcResponse.error(request.id(), -32602, "Unknown tool: " + name);
  }

  private Map<String, Object> listPrompts() {
    return Map.of(
        "prompts",
        List.of(
            Map.of(
                "name",
                "find-skill",
                "title",
                "Find a Skill",
                "description",
                "Help a user find a suitable Skill from the central catalog.",
                "arguments",
                List.of(Map.of("name", "task", "description", "User task", "required", true)))));
  }

  private JsonRpcResponse getPrompt(JsonRpcRequest request) {
    String name = stringParam(request, "name");
    if (!"find-skill".equals(name)) {
      return JsonRpcResponse.error(request.id(), -32004, "Prompt not found: " + name);
    }
    Map<String, Object> arguments = mapParam(request, "arguments");
    String task = stringValue(arguments, "task");
    return JsonRpcResponse.result(
        request.id(),
        Map.of(
            "description",
            "Find a Skill for a user task.",
            "messages",
            List.of(
                Map.of(
                    "role",
                    "user",
                    "content",
                    Map.of(
                        "type",
                        "text",
                        "text",
                        "Find the best Skill for this task using search_skills, then explain how to fetch it: "
                            + task)))));
  }

  private Map<String, Object> skillView(SkillEntry skill) {
    Map<String, Object> view = new LinkedHashMap<>();
    view.put("skill_id", skill.skillId());
    view.put("name", skill.name());
    view.put("description", skill.description());
    view.put("owner_department", skill.ownerDepartment());
    view.put("owner_team", skill.ownerTeam());
    view.put("tags", skill.tags());
    view.put("visibility_groups", skill.visibilityGroups());
    view.put("location", locationView(skill));
    if (skill.executable() != null) {
      view.put("executable", skill.executable());
    }
    return view;
  }

  private Map<String, Object> locationView(SkillEntry skill) {
    return Map.of(
        "repository_url",
        skill.repositoryUrl(),
        "ref",
        skill.ref(),
        "skill_path",
        skill.skillPath(),
        "manifest_path",
        skill.manifestPath().toString());
  }

  private Map<String, Object> textToolResult(String text) {
    return Map.of("content", List.of(Map.of("type", "text", "text", text)), "isError", false);
  }

  private Map<String, Object> objectSchema(Map<String, Object> properties) {
    return objectSchema(properties, List.of());
  }

  private Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
    return Map.of("type", "object", "properties", properties, "required", required);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> mapParam(JsonRpcRequest request, String key) {
    Object value = request.params() == null ? null : request.params().get(key);
    return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
  }

  private String stringParam(JsonRpcRequest request, String key) {
    return stringValue(request.params(), key);
  }

  private String stringValue(Map<String, Object> values, String key) {
    if (values == null || values.get(key) == null) {
      return null;
    }
    return values.get(key).toString();
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
    } catch (JacksonException e) {
      throw new IllegalStateException("Failed to serialize MCP payload", e);
    }
  }
}
