package dev.sobue.ai.skill.mcp.config;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "skill-mcp")
public class SkillMcpProperties {

  private final Scan scan = new Scan();

  public Scan getScan() {
    return scan;
  }

  public static class Scan {

    private List<Path> roots = new ArrayList<>(List.of(Path.of(".")));

    private long fixedDelayMillis = 300_000L;

    public List<Path> getRoots() {
      return roots;
    }

    public void setRoots(List<Path> roots) {
      this.roots = roots;
    }

    public long getFixedDelayMillis() {
      return fixedDelayMillis;
    }

    public void setFixedDelayMillis(long fixedDelayMillis) {
      this.fixedDelayMillis = fixedDelayMillis;
    }
  }
}
