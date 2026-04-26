package dev.sobue.ai.skill.mcp.config;

import java.nio.file.Path;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "skill-mcp")
public record SkillMcpProperties(Scan scan, GitHub github) {

  public SkillMcpProperties {
    scan = scan == null ? new Scan() : scan;
    github = github == null ? new GitHub() : github;
  }

  public SkillMcpProperties() {
    this(new Scan(), new GitHub());
  }

  public record Scan(List<Path> roots, long fixedDelayMillis) {

    public Scan {
      roots = roots == null || roots.isEmpty() ? List.of(Path.of(".")) : List.copyOf(roots);
    }

    public Scan() {
      this(List.of(Path.of(".")), 300_000L);
    }
  }

  public record GitHub(
      String baseApiUrl,
      String appId,
      String installationId,
      String privateKey,
      Path privateKeyPath,
      List<Repository> repositories) {

    public GitHub {
      baseApiUrl = StringUtils.hasText(baseApiUrl) ? baseApiUrl : "https://api.github.com";
      repositories = repositories == null ? List.of() : List.copyOf(repositories);
    }

    public GitHub() {
      this("https://api.github.com", null, null, null, null, List.of());
    }
  }

  public record Repository(String url, String ref) {

    public Repository {
      ref = StringUtils.hasText(ref) ? ref : "main";
    }

    public Repository(String url) {
      this(url, "main");
    }
  }
}
