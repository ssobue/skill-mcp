package dev.sobue.ai.skill.mcp.catalog;

import java.util.List;

public record GitHubTree(List<GitHubTreeItem> items, boolean truncated) {}
