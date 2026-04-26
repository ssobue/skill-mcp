package dev.sobue.ai.skill.mcp.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.sobue.ai.skill.mcp.config.SkillMcpProperties;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHApp;
import org.kohsuke.github.GHAppCreateTokenBuilder;
import org.kohsuke.github.GHAppInstallation;
import org.kohsuke.github.GHAppInstallationToken;
import org.kohsuke.github.GHBlob;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHTree;
import org.kohsuke.github.GHTreeEntry;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.mockito.MockedConstruction;

class GitHubApiClientTests {

  @Test
  void fetchesTreeAndBlobWithCachedInstallationClient() throws Exception {
    SkillMcpProperties properties =
        new SkillMcpProperties(
            new SkillMcpProperties.Scan(),
            new SkillMcpProperties.GitHub(
                "https://api.github.com", "12345", "67890", "private-key", null, List.of()));
    GitHubAppJwtProvider jwtProvider = mock(GitHubAppJwtProvider.class);
    GitHub appGitHub = mock(GitHub.class);
    GitHub installationGitHub = mock(GitHub.class);
    GHApp app = mock(GHApp.class);
    GHAppInstallation installation = mock(GHAppInstallation.class);
    GHAppCreateTokenBuilder tokenBuilder = mock(GHAppCreateTokenBuilder.class);
    GHAppInstallationToken token = installationToken();
    GHRepository repository = mock(GHRepository.class);
    GHTree tree = mock(GHTree.class);
    GHTreeEntry entry = mock(GHTreeEntry.class);
    GHBlob blob = mock(GHBlob.class);

    when(jwtProvider.createJwt()).thenReturn("jwt");
    when(appGitHub.getApp()).thenReturn(app);
    when(app.getInstallationById(67890L)).thenReturn(installation);
    when(installation.createToken()).thenReturn(tokenBuilder);
    when(tokenBuilder.create()).thenReturn(token);
    when(installationGitHub.getRepository("example/skills")).thenReturn(repository);
    when(repository.getTreeRecursive("main", 1)).thenReturn(tree);
    when(tree.getTree()).thenReturn(List.of(entry));
    when(tree.isTruncated()).thenReturn(true);
    when(entry.getPath()).thenReturn("skills/example/skill.yaml");
    when(entry.getType()).thenReturn("blob");
    when(entry.getSha()).thenReturn("manifest-sha");
    when(repository.getBlob("manifest-sha")).thenReturn(blob);
    when(blob.read()).thenReturn(new ByteArrayInputStream("skill_id: example".getBytes(StandardCharsets.UTF_8)));

    AtomicInteger builderCount = new AtomicInteger();
    try (MockedConstruction<GitHubBuilder> ignored =
        mockConstruction(
            GitHubBuilder.class,
            (builder, _) -> {
              int index = builderCount.incrementAndGet();
              when(builder.withEndpoint(anyString())).thenReturn(builder);
              when(builder.withJwtToken(anyString())).thenReturn(builder);
              when(builder.withAppInstallationToken(anyString())).thenReturn(builder);
              when(builder.build()).thenReturn(index == 1 ? appGitHub : installationGitHub);
            })) {
      GitHubApiClient client = new GitHubApiClient(properties, jwtProvider);
      GitHubRepositoryRef repositoryRef =
          new GitHubRepositoryRef("example", "skills", "https://github.com/example/skills.git", "main");

      GitHubTree fetchedTree = client.fetchTree(repositoryRef);
      String blobText = client.fetchBlobText(repositoryRef, "manifest-sha");

      assertThat(fetchedTree.truncated()).isTrue();
      assertThat(fetchedTree.items()).containsExactly(new GitHubTreeItem("skills/example/skill.yaml", "blob", "manifest-sha"));
      assertThat(blobText).isEqualTo("skill_id: example");
    }

    verify(jwtProvider).createJwt();
    verify(app, times(1)).getInstallationById(67890L);
    verify(installationGitHub, times(2)).getRepository("example/skills");
  }

  private GHAppInstallationToken installationToken() throws Exception {
    GHAppInstallationToken token = new GHAppInstallationToken();
    Field tokenValue = GHAppInstallationToken.class.getDeclaredField("token");
    tokenValue.setAccessible(true);
    tokenValue.set(token, "installation-token");
    Field expiresAt = GHAppInstallationToken.class.getDeclaredField("expires_at");
    expiresAt.setAccessible(true);
    expiresAt.set(token, Instant.now().plusSeconds(3600).toString());
    return token;
  }
}
