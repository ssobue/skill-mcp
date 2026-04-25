package dev.sobue.ai.skill.mcp.catalog;

import java.time.Instant;
import java.util.List;

public record CatalogSnapshot(List<SkillEntry> skills, List<String> warnings, Instant refreshedAt) {

  public static CatalogSnapshot empty() {
    return new CatalogSnapshot(List.of(), List.of(), Instant.EPOCH);
  }
}
