package dev.sobue.ai.skill.mcp.mcp;

import dev.sobue.ai.skill.mcp.catalog.SkillCatalogService;
import dev.sobue.ai.skill.mcp.catalog.SkillEntry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
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
  private static final String KEY_ARGUMENTS = "arguments";
  private static final String KEY_DESCRIPTION = "description";
  private static final String KEY_MIME_TYPE = "mimeType";
  private static final String KEY_NAME = "name";
  private static final String KEY_SKILL_ID = "skill_id";
  private static final String KEY_TEXT = "text";
  private static final String KEY_TITLE = "title";
  private static final String KEY_TYPE = "type";
  private static final String KEY_URI = "uri";
  private static final String MEDIA_TYPE_JSON = "application/json";
  private static final String OWNER_DEPARTMENT = "owner_department";
  private static final String OWNER_TEAM = "owner_team";
  private static final String SCHEMA_TYPE_STRING = "string";
  private static final String SKILL_URI_PREFIX = "skill://";

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
    Map<String, Object> capability = Map.of("listChanged", false);
    return Map.of(
        "protocolVersion",
        PROTOCOL_VERSION,
        "capabilities",
        Map.of("resources", capability, "tools", capability, "prompts", capability),
        "serverInfo",
        Map.of(KEY_NAME, "skill-mcp", "version", "0.0.1"));
  }

  private Map<String, Object> listResources() {
    List<Map<String, Object>> resources =
        catalogService.snapshot().skills().stream()
            .map(
                skill ->
                    Map.<String, Object>of(
                        KEY_URI, skillUri(skill.skillId()),
                        KEY_NAME, skill.skillId(),
                        KEY_TITLE, skill.name(),
                        KEY_DESCRIPTION, skill.description(),
                        KEY_MIME_TYPE, MEDIA_TYPE_JSON))
            .toList();
    return Map.of("resources", resources);
  }

  private JsonRpcResponse readResource(JsonRpcRequest request) {
    String uri = stringParam(request, KEY_URI);
    if (uri == null || !uri.startsWith(SKILL_URI_PREFIX)) {
      return JsonRpcResponse.error(request.id(), -32602, "resources/read requires uri skill://<skill_id>");
    }
    String skillId = uri.substring(SKILL_URI_PREFIX.length());
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
                KEY_URI,
                skillUri(skill.skillId()),
                KEY_MIME_TYPE,
                MEDIA_TYPE_JSON,
                KEY_TEXT,
                toJson(skillView(skill)))));
  }

  private Map<String, Object> resourceTemplates() {
    return Map.of(
        "resourceTemplates",
        List.of(
            Map.of(
                "uriTemplate",
                "skill://{skill_id}",
                KEY_NAME,
                "skill",
                KEY_TITLE,
                "Skill metadata",
                KEY_DESCRIPTION,
                "Read metadata and location for a discovered Skill.",
                KEY_MIME_TYPE,
                MEDIA_TYPE_JSON)));
  }

  private Map<String, Object> listTools() {
    return Map.of(
        "tools",
        List.of(
            Map.of(
                KEY_NAME,
                "search_skills",
                KEY_DESCRIPTION,
                "Search discovered Skills by keyword, tag, department, or team.",
                "inputSchema",
                objectSchema(
                    Map.of(
                        "query", stringSchema(),
                        "tag", stringSchema(),
                        OWNER_DEPARTMENT, stringSchema(),
                        OWNER_TEAM, stringSchema()))),
            Map.of(
                KEY_NAME,
                "get_skill_location",
                KEY_DESCRIPTION,
                "Return Git repository location details for a Skill.",
                "inputSchema",
                objectSchema(Map.of(KEY_SKILL_ID, stringSchema()), List.of(KEY_SKILL_ID)))));
  }

  private JsonRpcResponse callTool(JsonRpcRequest request) {
    String name = stringParam(request, KEY_NAME);
    Map<String, Object> arguments = mapParam(request, KEY_ARGUMENTS);
    if ("search_skills".equals(name)) {
      List<Map<String, Object>> skills =
          catalogService
              .search(
                  stringValue(arguments, "query"),
                  stringValue(arguments, "tag"),
                  stringValue(arguments, OWNER_DEPARTMENT),
                  stringValue(arguments, OWNER_TEAM))
              .stream()
              .map(this::skillView)
              .toList();
      return JsonRpcResponse.result(request.id(), textToolResult(toJson(Map.of("skills", skills))));
    }
    if ("get_skill_location".equals(name)) {
      String skillId = stringValue(arguments, KEY_SKILL_ID);
      if (skillId == null) {
        return JsonRpcResponse.error(request.id(), -32602, "get_skill_location requires skill_id");
      }
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
                KEY_NAME,
                "find-skill",
                KEY_TITLE,
                "Find a Skill",
                KEY_DESCRIPTION,
                "Help a user find a suitable Skill from the central catalog.",
                KEY_ARGUMENTS,
                List.of(Map.of(KEY_NAME, "task", KEY_DESCRIPTION, "User task", "required", true)))));
  }

  private JsonRpcResponse getPrompt(JsonRpcRequest request) {
    String name = stringParam(request, KEY_NAME);
    if (!"find-skill".equals(name)) {
      return JsonRpcResponse.error(request.id(), -32004, "Prompt not found: " + name);
    }
    Map<String, Object> arguments = mapParam(request, KEY_ARGUMENTS);
    String task = stringValue(arguments, "task");
    return JsonRpcResponse.result(
        request.id(),
        Map.of(
            KEY_DESCRIPTION,
            "Find a Skill for a user task.",
            "messages",
            List.of(
                Map.of(
                    "role",
                    "user",
                    "content",
                    Map.of(
                        KEY_TYPE,
                        KEY_TEXT,
                        KEY_TEXT,
                        "Find the best Skill for this task using search_skills, then explain how to fetch it: "
                            + task)))));
  }

  private Map<String, Object> skillView(SkillEntry skill) {
    Map<String, Object> view = new LinkedHashMap<>();
    view.put(KEY_SKILL_ID, skill.skillId());
    view.put(KEY_NAME, skill.name());
    view.put(KEY_DESCRIPTION, skill.description());
    view.put(OWNER_DEPARTMENT, skill.ownerDepartment());
    view.put(OWNER_TEAM, skill.ownerTeam());
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
    return Map.of("content", List.of(Map.of(KEY_TYPE, KEY_TEXT, KEY_TEXT, text)), "isError", false);
  }

  private Map<String, Object> objectSchema(Map<String, Object> properties) {
    return objectSchema(properties, List.of());
  }

  private Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
    return Map.of(KEY_TYPE, "object", "properties", properties, "required", required);
  }

  private Map<String, Object> stringSchema() {
    return Map.of(KEY_TYPE, SCHEMA_TYPE_STRING);
  }

  private String skillUri(String skillId) {
    return SKILL_URI_PREFIX + skillId;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> mapParam(JsonRpcRequest request, String key) {
    Object value = request.params() == null ? null : request.params().get(key);
    return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
  }

  @Nullable
  private String stringParam(JsonRpcRequest request, String key) {
    return stringValue(request.params(), key);
  }

  @Nullable
  private String stringValue(@Nullable Map<String, Object> values, String key) {
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
