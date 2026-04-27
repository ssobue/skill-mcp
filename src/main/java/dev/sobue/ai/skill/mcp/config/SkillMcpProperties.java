package dev.sobue.ai.skill.mcp.config;

import java.nio.file.Path;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "skill-mcp")
public record SkillMcpProperties(Scan scan, GitHub github) {

  public SkillMcpProperties(@Nullable Scan scan, @Nullable GitHub github) {
    scan = scan == null ? new Scan() : scan;
    github = github == null ? new GitHub() : github;
    this.scan = scan;
    this.github = github;
  }

  public SkillMcpProperties() {
    this(new Scan(), new GitHub());
  }

  public record Scan(List<Path> roots, long fixedDelayMillis) {

    public Scan(@Nullable List<Path> roots, long fixedDelayMillis) {
      roots = roots == null || roots.isEmpty() ? List.of(Path.of(".")) : List.copyOf(roots);
      this.roots = roots;
      this.fixedDelayMillis = fixedDelayMillis;
    }

    public Scan() {
      this(List.of(Path.of(".")), 300_000L);
    }
  }

  public record GitHub(
      String baseApiUrl,
      @Nullable
      String appId,
      @Nullable
      String installationId,
      @Nullable
      String privateKey,
      @Nullable
      Path privateKeyPath,
      List<Repository> repositories) {

    public GitHub(
        @Nullable String baseApiUrl,
        @Nullable String appId,
        @Nullable String installationId,
        @Nullable String privateKey,
        @Nullable Path privateKeyPath,
        @Nullable List<Repository> repositories) {
      baseApiUrl = StringUtils.hasText(baseApiUrl) ? baseApiUrl : "https://api.github.com";
      repositories = repositories == null ? List.of() : List.copyOf(repositories);
      this.baseApiUrl = baseApiUrl;
      this.appId = appId;
      this.installationId = installationId;
      this.privateKey = privateKey;
      this.privateKeyPath = privateKeyPath;
      this.repositories = repositories;
    }

    public GitHub() {
      this("https://api.github.com", null, null, null, null, List.of());
    }
  }

  public record Repository(@Nullable String url, String ref) {

    public Repository(@Nullable String url, @Nullable String ref) {
      ref = StringUtils.hasText(ref) ? ref : "main";
      this.url = url;
      this.ref = ref;
    }

    public Repository(String url) {
      this(url, "main");
    }
  }
}
