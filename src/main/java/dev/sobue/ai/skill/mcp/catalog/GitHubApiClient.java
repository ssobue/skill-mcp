package dev.sobue.ai.skill.mcp.catalog;

import dev.sobue.ai.skill.mcp.config.SkillMcpProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import org.kohsuke.github.GHAppInstallation;
import org.kohsuke.github.GHAppInstallationToken;
import org.kohsuke.github.GHBlob;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHTree;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class GitHubApiClient implements GitHubClient {

  private final SkillMcpProperties properties;
  private final GitHubAppJwtProvider jwtProvider;
  private InstallationGitHubCache installationGitHubCache;

  @Autowired
  public GitHubApiClient(SkillMcpProperties properties, GitHubAppJwtProvider jwtProvider) {
    this.properties = properties;
    this.jwtProvider = jwtProvider;
  }

  @Override
  public GitHubTree fetchTree(GitHubRepositoryRef repository) throws IOException {
    GHRepository ghRepository = installationGitHub().getRepository(repository.owner() + "/" + repository.name());
    GHTree tree = ghRepository.getTreeRecursive(repository.ref(), 1);
    return new GitHubTree(
        tree.getTree().stream()
            .map(item -> new GitHubTreeItem(item.getPath(), item.getType(), item.getSha()))
            .toList(),
        tree.isTruncated());
  }

  @Override
  public String fetchBlobText(GitHubRepositoryRef repository, String sha) throws IOException {
    GHRepository ghRepository = installationGitHub().getRepository(repository.owner() + "/" + repository.name());
    GHBlob blob = ghRepository.getBlob(sha);
    try (var input = blob.read()) {
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private synchronized GitHub installationGitHub() throws IOException {
    if (installationGitHubCache != null && installationGitHubCache.isValid()) {
      return installationGitHubCache.github();
    }
    String jwt = jwtProvider.createJwt();
    GitHub appGitHub =
        new GitHubBuilder().withEndpoint(properties.getGithub().getBaseApiUrl()).withJwtToken(jwt).build();
    GHAppInstallation installation =
        appGitHub.getApp().getInstallationById(Long.parseLong(properties.getGithub().getInstallationId()));
    GHAppInstallationToken token = installation.createToken().create();
    GitHub installationGitHub =
        new GitHubBuilder()
            .withEndpoint(properties.getGithub().getBaseApiUrl())
            .withAppInstallationToken(token.getToken())
            .build();
    installationGitHubCache =
        new InstallationGitHubCache(
            installationGitHub, token.getExpiresAt().toInstant().minus(Duration.ofMinutes(5)));
    return installationGitHub;
  }

  private record InstallationGitHubCache(GitHub github, Instant refreshAfter) {
    boolean isValid() {
      return refreshAfter.isAfter(Instant.now());
    }
  }
}
