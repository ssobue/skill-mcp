# ADR 0003: Build a Custom Lightweight Skill Catalog

## Status

Accepted

## Context

Skill MCP needs to serve about 200 internal users who discover Skills owned by multiple departments. Each department should keep its Skill source in its own Git repository. General employees should be able to publish or update a Skill by changing files in a repository they already control.

The v1 constraints are intentionally small:

- Git remains the source of truth.
- GitHub access uses a GitHub App, not personal access tokens.
- No database is introduced.
- No authentication, authorization, or audit logging is introduced yet.
- The central server does not execute Skill code.
- The server exposes Skills through MCP so existing agents can discover and fetch them.

We investigated existing products and OSS before deciding to build this service.

## Alternatives Considered

### Official MCP Registry

The official MCP Registry provides centralized metadata for publicly accessible MCP servers.

References:

- https://modelcontextprotocol.io/registry/about
- https://registry.modelcontextprotocol.io/
- https://github.com/modelcontextprotocol/registry

It is a strong fit for public MCP server discovery, but not for this v1 because:

- It catalogs MCP servers, not organization-specific Skill folders.
- The hosted registry is intended for publicly accessible servers and does not support private servers.
- The documentation recommends hosting a private MCP registry for private servers, but the official codebase is not presented as a supported self-hosted product.
- Operating it would introduce more platform surface than needed for a small Skill catalog.

### ToolSDK MCP Registry

ToolSDK MCP Registry is an OSS registry and gateway for discovering and executing MCP servers and tools.

Reference:

- https://github.com/toolsdk-ai/toolsdk-mcp-registry

It is useful when the goal is a broader MCP tool gateway with APIs, sandbox execution, OAuth-oriented behavior, and a larger registry model. It is not the best v1 fit because Skill MCP deliberately avoids central tool execution, sandbox operation, and database-backed gateway behavior.

### MCP Foundry

MCP Foundry is a public registry for security-scanned MCP tool servers.

Reference:

- https://mcpfoundry.org/

It is aimed at community MCP tool discovery and verification. It does not address private department-owned Skill repositories or GitHub App based cataloging of internal Skill definitions.

### MCPM

MCPM is an open source MCP server manager.

Reference:

- https://mcpm.sh/

It helps users discover, install, profile, configure, and run MCP servers across clients. That is different from a central internal Skill catalog. It manages MCP server installation and client configuration rather than indexing Skill manifests from multiple internal Git repositories.

### Agent Skills and Claude Code Skills

Agent Skills define a portable Skill folder format, and Claude Code supports Skills from personal, project, plugin, and enterprise locations.

References:

- https://agentskills.io/
- https://code.claude.com/docs/en/skills
- https://support.claude.com/en/articles/12512180-use-skills-in-claude

These are highly relevant to the Skill content format, but they do not replace this server. They define how agents consume Skills, while Skill MCP provides an organization-owned discovery layer over multiple Git repositories. Product-managed enterprise Skill provisioning may work for some Claude-only environments, but this project needs an MCP-facing catalog that can be used by multiple MCP clients and that preserves department repository ownership.

## Decision

Build and maintain a custom lightweight Skill catalog MCP server.

The server will:

- Scan local repository roots and configured GitHub repositories.
- Read `skill.yaml` manifests and validate that matching `SKILL.md` files exist.
- Use GitHub App installation access through Hub4j when scanning GitHub directly.
- Keep the catalog in memory.
- Expose discovery through MCP resources, tools, and prompts.
- Return repository location metadata so user-side agents can fetch and run Skills locally when appropriate.

## Consequences

The implementation is smaller than adopting a general MCP registry or gateway and better aligned with the current constraints.

Skill authors keep a low-friction Git workflow. They do not need to publish a package, operate a new registry client, or ask for central server code changes when adding ordinary Skills.

The project intentionally leaves some capabilities for later:

- Authorization and `visibility_groups` enforcement.
- Audit logging.
- Management UI.
- Database-backed search and history.
- Rich compatibility with the official MCP Registry API.
- Optional validation against the Agent Skills specification.

This decision can be revisited if the catalog grows beyond in-memory search, if private MCP registry interoperability becomes more important than Skill-specific behavior, or if an existing product gains first-class support for private multi-repository Skill catalogs with GitHub App authentication.
