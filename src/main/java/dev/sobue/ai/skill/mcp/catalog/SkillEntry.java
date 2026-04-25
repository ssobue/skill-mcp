package dev.sobue.ai.skill.mcp.catalog;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

public record SkillEntry(
    String skillId,
    String name,
    String description,
    String ownerDepartment,
    String ownerTeam,
    List<String> tags,
    List<String> visibilityGroups,
    ExecutableDefinition executable,
    String repositoryUrl,
    String ref,
    String skillPath,
    Path repositoryRoot,
    Path manifestPath,
    Path skillDirectory,
    Instant indexedAt) {}
