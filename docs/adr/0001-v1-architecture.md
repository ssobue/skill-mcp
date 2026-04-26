# ADR 0001: v1 Architecture

## Status

Accepted

## Context

Skills are owned by multiple departments and teams. The desired publishing path should be easy for general employees: add Skill files to their own Git repository and let the central server discover them.

The initial user base is around 200 people. The first version should avoid operational dependencies that are not yet necessary.

## Decision

Build Skill MCP as a Java 25 Spring Boot service using the package namespace `dev.sobue.ai.skill.mcp`.

For v1:

- Use distributed Git repositories as the source of Skill content.
- Discover Skills by scanning `skill.yaml` manifests from local worktrees and configured GitHub repositories.
- Keep the catalog in memory.
- Do not use a database.
- Do not implement authentication or authorization.
- Do not implement audit logging.
- Do not execute Skill source code on the central server.
- Expose catalog access through MCP-compatible JSON-RPC methods at `/mcp`.

## Consequences

Publishing is simple for teams because they only need to add `SKILL.md` and `skill.yaml` to their own repository.

Operations are simple because there is no database, auth integration, or execution sandbox.

All discovered Skills are visible to all users in v1. `visibility_groups` can be captured in manifests now, but enforcement is deferred.

The catalog is rebuilt from local Git/worktree scans and GitHub scans after restart. This is acceptable for v1 because Git remains the source of truth.

Executable Skills are safer to support because execution happens in the user's workspace, under the user's agent approval model, instead of on the central server.
