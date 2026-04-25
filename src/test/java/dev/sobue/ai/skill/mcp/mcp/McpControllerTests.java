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
