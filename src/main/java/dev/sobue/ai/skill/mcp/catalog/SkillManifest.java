package dev.sobue.ai.skill.mcp.catalog;

import java.util.List;
import org.jspecify.annotations.Nullable;

public record SkillManifest(
    String skillId,
    String name,
    String description,
    String ownerDepartment,
    String ownerTeam,
    List<String> tags,
    String skillPath,
    List<String> visibilityGroups,
    @Nullable
    ExecutableDefinition executable) {}
