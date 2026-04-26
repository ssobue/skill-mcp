package dev.sobue.ai.skill.mcp.catalog;

import java.io.IOException;

public interface GitHubClient {

  GitHubTree fetchTree(GitHubRepositoryRef repository) throws IOException, InterruptedException;

  String fetchBlobText(GitHubRepositoryRef repository, String sha) throws IOException, InterruptedException;
}
