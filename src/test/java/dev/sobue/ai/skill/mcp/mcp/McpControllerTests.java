package dev.sobue.ai.skill.mcp.mcp;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.sobue.ai.skill.mcp.catalog.CatalogSnapshot;
import dev.sobue.ai.skill.mcp.catalog.SkillCatalogService;
import dev.sobue.ai.skill.mcp.catalog.SkillEntry;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

class McpControllerTests {

  private final SkillCatalogService catalogService = Mockito.mock(SkillCatalogService.class);

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new McpController(catalogService, JsonMapper.builder().findAndAddModules().build()))
            .build();
  }

  @Test
  void listsResources() throws Exception {
    when(catalogService.snapshot())
        .thenReturn(new CatalogSnapshot(List.of(skill()), List.of(), Instant.now()));

    mockMvc
        .perform(
            post("/mcp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"jsonrpc":"2.0","id":1,"method":"resources/list","params":{}}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.resources[0].uri").value("skill://example-skill"));
  }

  @Test
  void searchesSkills() throws Exception {
    when(catalogService.search("example", null, null, null)).thenReturn(List.of(skill()));

    mockMvc
        .perform(
            post("/mcp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "jsonrpc": "2.0",
                      "id": 2,
                      "method": "tools/call",
                      "params": {
                        "name": "search_skills",
                        "arguments": {"query": "example"}
                      }
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.content[0].text", containsString("example-skill")));
  }

  @Test
  void initializesServerCapabilities() throws Exception {
    mockMvc
        .perform(
            post("/mcp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"jsonrpc":"2.0","id":3,"method":"initialize","params":{}}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.protocolVersion").value("2025-11-25"))
        .andExpect(jsonPath("$.result.serverInfo.name").value("skill-mcp"));
  }

  @Test
  void returnsNoContentForNotification() throws Exception {
    mockMvc
        .perform(
            post("/mcp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"jsonrpc":"2.0","method":"notifications/initialized","params":{}}
                    """))
        .andExpect(status().isNoContent());
  }

  @Test
  void readsResource() throws Exception {
    when(catalogService.findById("example-skill")).thenReturn(Optional.of(skill()));

    mockMvc
        .perform(
            post("/mcp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"jsonrpc":"2.0","id":4,"method":"resources/read","params":{"uri":"skill://example-skill"}}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.contents[0].uri").value("skill://example-skill"))
        .andExpect(jsonPath("$.result.contents[0].text", containsString("Example Skill")));
  }

  @Test
  void rejectsInvalidResourceUri() throws Exception {
    mockMvc
        .perform(
            post("/mcp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"jsonrpc":"2.0","id":5,"method":"resources/read","params":{"uri":"file://example"}}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.error.code").value(-32602));
  }

  @Test
  void returnsResourceTemplatesAndToolsAndPrompts() throws Exception {
    mockMvc
        .perform(
            post("/mcp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"jsonrpc":"2.0","id":6,"method":"resources/templates/list","params":{}}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.resourceTemplates[0].uriTemplate").value("skill://{skill_id}"));

    mockMvc
        .perform(
            post("/mcp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"jsonrpc":"2.0","id":7,"method":"tools/list","params":{}}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.tools[0].name").value("search_skills"));

    mockMvc
        .perform(
            post("/mcp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"jsonrpc":"2.0","id":8,"method":"prompts/list","params":{}}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.prompts[0].name").value("find-skill"));
  }

  @Test
  void getsPrompt() throws Exception {
    mockMvc
        .perform(
            post("/mcp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "jsonrpc": "2.0",
                      "id": 9,
                      "method": "prompts/get",
                      "params": {
                        "name": "find-skill",
                        "arguments": {"task": "generate an API"}
                      }
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.messages[0].content.text", containsString("generate an API")));
  }

  @Test
  void returnsSkillLocation() throws Exception {
    when(catalogService.findById("example-skill")).thenReturn(Optional.of(skill()));

    mockMvc
        .perform(
            post("/mcp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "jsonrpc": "2.0",
                      "id": 10,
                      "method": "tools/call",
                      "params": {
                        "name": "get_skill_location",
                        "arguments": {"skill_id": "example-skill"}
                      }
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result.content[0].text", containsString("https://example.com/repo.git")));
  }

  @Test
  void returnsErrorsForUnknownItems() throws Exception {
    when(catalogService.findById("missing")).thenReturn(Optional.empty());

    mockMvc
        .perform(
            post("/mcp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"jsonrpc":"2.0","id":11,"method":"tools/call","params":{"name":"unknown","arguments":{}}}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.error.code").value(-32602));

    mockMvc
        .perform(
            post("/mcp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"jsonrpc":"2.0","id":12,"method":"prompts/get","params":{"name":"missing","arguments":{}}}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.error.code").value(-32004));

    mockMvc
        .perform(
            post("/mcp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"jsonrpc":"2.0","id":13,"method":"missing/method","params":{}}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.error.code").value(-32601));
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
