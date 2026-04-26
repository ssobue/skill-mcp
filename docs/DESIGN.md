# Skill MCP Design

## Overview

Skill MCP is a central catalog for Skills stored in multiple Git repositories owned by departments or teams.

The server does not store Skill source code. It scans configured local repository roots and configured GitHub repositories, reads `skill.yaml` manifests, validates each Skill directory, and keeps the resulting catalog in memory.

## Background

The project was created after comparing existing MCP registry, MCP manager, and Agent Skills offerings. Those projects are useful, but they solve adjacent problems:

- The official MCP Registry is a public metadata registry for MCP servers. It explicitly focuses on publicly accessible servers and recommends a private registry for private servers. Its hosted registry is also in preview, and its codebase is not positioned as a supported self-hosting product.
- MCP server registries and gateways such as ToolSDK MCP Registry, MCP Foundry, and MCPM primarily discover, install, proxy, or execute MCP servers and tools.
- Agent Skills and Claude Code Skills define and use Skill folders, but they do not provide a small organization-owned MCP catalog that can index several department-owned GitHub repositories through a GitHub App.

For this project, the desired first version is narrower: help about 200 internal users find organization-specific Skills that remain owned by their departments in Git. The catalog must be easy for employees to publish to, avoid personal access tokens, avoid a database, and avoid central execution of Skill code.

See [ADR 0003](adr/0003-custom-skill-catalog.md) for the alternatives considered.

## Goals

- Let general employees publish Skills by adding `SKILL.md` and `skill.yaml` to their own repositories.
- Provide a central MCP endpoint for searching and locating Skills.
- Keep v1 small enough to operate without a database, authentication, or a sandbox execution platform.
- Preserve a clear boundary: the MCP server catalogs executable metadata, but user-side agents execute code.

## Non-Goals

- Authentication and authorization.
- Audit logging.
- Database-backed persistence.
- Management UI.
- Full-text indexing of all Skill content.
- Server-side execution of Skill code.
- Automatic installation into user environments.

## Runtime Architecture

The application is a Java 25 Spring Boot service.

Main package:

```text
dev.sobue.ai.skill.mcp
```

Main components:

- `SkillScanner`: finds `skill.yaml` in local worktrees, parses manifests, validates `SKILL.md`, and builds catalog entries.
- `GitHubSkillScanner`: scans configured GitHub repositories through a `GitHubClient` abstraction.
- `GitHubApiClient`: uses Hub4j GitHub API to authenticate as a GitHub App installation and read repository trees and blobs.
- `SkillCatalogService`: owns the current in-memory `CatalogSnapshot` and refreshes it on startup and schedule.
- `McpController`: exposes JSON-RPC MCP methods at `/mcp`.

JSON serialization uses Spring Boot managed Jackson 3. YAML manifest parsing uses SnakeYAML, matching Spring Boot's YAML stack instead of Jackson YAML.

## Catalog Refresh

The server scans configured roots on startup and on a fixed delay.

```yaml
skill-mcp:
  scan:
    roots:
      - /path/to/skill-repository
      - /path/to/department-repositories
    fixed-delay-millis: 300000
  github:
    app-id: ${GITHUB_APP_ID:}
    installation-id: ${GITHUB_APP_INSTALLATION_ID:}
    private-key-path: ${GITHUB_APP_PRIVATE_KEY_PATH:}
    repositories:
      - url: https://github.com/example/team-skills.git
        ref: main
```

Local roots are scanned from the filesystem. GitHub repositories are scanned without checkout by using Hub4j GitHub API to read recursive repository trees and blob contents.

GitHub access uses GitHub App authentication. The server signs a short-lived JWT with the app private key, uses Hub4j to create an installation access token, caches the resulting GitHub client until near token expiry, and uses it for repository API requests. Personal access tokens are intentionally not part of the design.

Each scan builds a new immutable snapshot. The current implementation replaces the old snapshot after scanning. Invalid manifests are skipped and reported as warnings in the snapshot.

## Skill Manifest

Required fields:

- `skill_id`
- `name`
- `description`
- `owner_department`
- `owner_team`
- `tags`
- `skill_path`

Optional fields:

- `visibility_groups`
- `executable`

`visibility_groups` is retained for future authorization but is not enforced in v1.

`executable` describes how a user-side agent can run the Skill locally. It must not imply execution on the central MCP server.

## MCP Methods

The `/mcp` endpoint accepts JSON-RPC requests.

Supported protocol methods:

- `initialize`
- `resources/list`
- `resources/read`
- `resources/templates/list`
- `tools/list`
- `tools/call`
- `prompts/list`
- `prompts/get`

Resources use the URI form:

```text
skill://<skill_id>
```

Tools:

- `search_skills`
- `get_skill_location`

Prompt:

- `find-skill`

## Execution Model

Executable Skills are not run by the MCP server.

The intended flow is:

1. User asks an MCP client or agent to find a Skill.
2. Agent calls `search_skills` or `resources/list`.
3. Agent calls `get_skill_location` or `resources/read`.
4. Agent fetches the Skill from Git into the user's workspace.
5. Agent reads `SKILL.md` and `skill.yaml`.
6. If execution is needed, the agent asks for approval and runs locally according to the user's environment policy.

## Scaling Assumption

The v1 target is around 200 users. The catalog is expected to be small enough for in-memory search. Horizontal scaling is possible by running multiple instances, each independently scanning the same configured local roots and GitHub repositories.
