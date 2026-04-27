package dev.sobue.ai.skill.mcp.mcp;

import java.util.Map;
import org.jspecify.annotations.Nullable;

public record JsonRpcRequest(
    String jsonrpc, @Nullable Object id, String method, @Nullable Map<String, Object> params) {}
