package dev.sobue.ai.skill.mcp.catalog;

import java.util.List;
import java.util.Map;

public record ExecutableDefinition(
    String type,
    String runtime,
    String workingDirectory,
    String command,
    List<String> args,
    List<Map<String, Object>> inputs,
    Map<String, Object> permissions) {}
