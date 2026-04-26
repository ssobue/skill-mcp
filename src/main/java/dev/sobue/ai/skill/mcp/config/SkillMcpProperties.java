package dev.sobue.ai.skill.mcp.config;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "skill-mcp")
public class SkillMcpProperties {

  private final Scan scan = new Scan();

  private final GitHub github = new GitHub();

  public Scan getScan() {
    return scan;
  }

  public GitHub getGithub() {
    return github;
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

  public static class GitHub {

    private String baseApiUrl = "https://api.github.com";

    private String appId;

    private String installationId;

    private String privateKey;

    private Path privateKeyPath;

    private List<Repository> repositories = new ArrayList<>();

    public String getBaseApiUrl() {
      return baseApiUrl;
    }

    public void setBaseApiUrl(String baseApiUrl) {
      this.baseApiUrl = baseApiUrl;
    }

    public String getAppId() {
      return appId;
    }

    public void setAppId(String appId) {
      this.appId = appId;
    }

    public String getInstallationId() {
      return installationId;
    }

    public void setInstallationId(String installationId) {
      this.installationId = installationId;
    }

    public String getPrivateKey() {
      return privateKey;
    }

    public void setPrivateKey(String privateKey) {
      this.privateKey = privateKey;
    }

    public Path getPrivateKeyPath() {
      return privateKeyPath;
    }

    public void setPrivateKeyPath(Path privateKeyPath) {
      this.privateKeyPath = privateKeyPath;
    }

    public List<Repository> getRepositories() {
      return repositories;
    }

    public void setRepositories(List<Repository> repositories) {
      this.repositories = repositories;
    }
  }

  public static class Repository {

    private String url;

    private String ref = "main";

    public String getUrl() {
      return url;
    }

    public void setUrl(String url) {
      this.url = url;
    }

    public String getRef() {
      return ref;
    }

    public void setRef(String ref) {
      this.ref = ref;
    }
  }
}
