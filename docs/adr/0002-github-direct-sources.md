# ADR 0002: GitHub Direct Skill Sources

## Status

Accepted

## Context

The first implementation supported local filesystem and checked-out Git repositories. Skill repositories may also live on GitHub and should be discoverable without cloning them locally on the MCP server.

## Decision

Add `skill-mcp.github.repositories` configuration for directly scanning GitHub repositories.

The server uses:

- Hub4j GitHub API as the Java client library for GitHub access.
- Recursive repository tree reads to find `skill.yaml`.
- Blob reads to read manifest content.
- GitHub App authentication using `app-id`, `installation-id`, and an app private key.
- Installation access tokens generated through Hub4j's GitHub App installation token flow.

The server still does not store Skill source code and still does not execute Skill code.

## Consequences

Repositories can be cataloged without local checkout.

Private repositories require the GitHub App installation to have repository contents read access.

Personal access tokens are not used.

Very large repositories can produce truncated tree responses. The server records a warning when GitHub reports truncation.
