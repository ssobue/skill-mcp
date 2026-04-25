package dev.sobue.ai.skill.mcp.catalog;

import java.util.List;

public record SkillManifest(
    String skillId,
    String name,
    String description,
    String ownerDepartment,
    String ownerTeam,
    List<String> tags,
    String skillPath,
    List<String> visibilityGroups,
    ExecutableDefinition executable) {}
