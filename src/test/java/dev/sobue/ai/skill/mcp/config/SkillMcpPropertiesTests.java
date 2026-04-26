package dev.sobue.ai.skill.mcp.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class SkillMcpPropertiesTests {

  @Test
  void defaultsMissingConfigurationValues() {
    SkillMcpProperties properties = new SkillMcpProperties(null, null);

    assertThat(properties.scan().roots()).containsExactly(Path.of("."));
    assertThat(properties.scan().fixedDelayMillis()).isEqualTo(300_000L);
    assertThat(properties.github().baseApiUrl()).isEqualTo("https://api.github.com");
    assertThat(properties.github().repositories()).isEmpty();
  }

  @Test
  void normalizesNestedDefaults() {
    SkillMcpProperties.Scan scan = new SkillMcpProperties.Scan(List.of(), 1_000L);
    SkillMcpProperties.GitHub github =
        new SkillMcpProperties.GitHub("", null, null, null, null, null);
    SkillMcpProperties.Repository repository = new SkillMcpProperties.Repository("https://github.com/example/repo", "");

    assertThat(scan.roots()).containsExactly(Path.of("."));
    assertThat(github.baseApiUrl()).isEqualTo("https://api.github.com");
    assertThat(github.repositories()).isEmpty();
    assertThat(repository.ref()).isEqualTo("main");
  }
}
