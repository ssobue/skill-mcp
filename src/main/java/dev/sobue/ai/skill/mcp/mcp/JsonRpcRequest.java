package dev.sobue.ai.skill.mcp.mcp;

import java.util.Map;

public record JsonRpcRequest(String jsonrpc, Object id, String method, Map<String, Object> params) {}
