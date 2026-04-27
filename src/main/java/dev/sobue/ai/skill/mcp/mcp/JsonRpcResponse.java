package dev.sobue.ai.skill.mcp.mcp;

import org.jspecify.annotations.Nullable;

public record JsonRpcResponse(
    String jsonrpc, @Nullable Object id, @Nullable Object result, @Nullable JsonRpcError error) {

  public static JsonRpcResponse result(@Nullable Object id, Object result) {
    return new JsonRpcResponse("2.0", id, result, null);
  }

  public static JsonRpcResponse error(@Nullable Object id, int code, String message) {
    return new JsonRpcResponse("2.0", id, null, new JsonRpcError(code, message));
  }
}
