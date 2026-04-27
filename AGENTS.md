# AGENTS.md

## Project

This repository contains Skill MCP, a Spring Boot based MCP catalog server for Skills stored in distributed Git repositories.

## Language and Build

- Use Java 25.
- Follow the lightweight Spring Boot project style used by `https://github.com/ssobue/demo`.
- The base package is `dev.sobue.ai.skill.mcp`.
- Use Maven Wrapper for builds.
- Use Spring Boot managed Jackson 3 for JSON.
- Use Spring AI MCP server starter and annotations for MCP transport and capabilities.
- Use SnakeYAML for `skill.yaml`; do not add Jackson YAML for manifest parsing.
- Use Hub4j GitHub API for GitHub repository access.
- Main source packages are JSpecify `@NullMarked`; keep `package-info.java` present for new packages.
- `./mvnw verify` runs NullAway for main source null checks. Use `@Nullable` only where `null` is part of the public or external-input contract.
- Run tests with:

```bash
./mvnw test
```

Run the full verification build with:

```bash
./mvnw verify
```

Build a GraalVM native executable with:

```bash
./mvnw -Pnative native:compile
```

If a build fails because of Java version differences, verify with more than one installed JDK. Use `/usr/libexec/java_home` to switch `JAVA_HOME`.

Example:

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 25)"
```

## Architecture Rules

- Keep Git as the source of truth for Skill definitions.
- Support both local worktree sources and configured GitHub repository sources.
- Do not introduce a database in v1.
- Do not add authentication or authorization in v1.
- Do not add audit logging in v1.
- Do not execute Skill source code on the central server.
- Treat executable Skill metadata as instructions for user-side agents.
- Keep MCP capabilities compatible with Spring AI MCP server transport and MCP clients.

## Skill Manifest Rules

Required `skill.yaml` fields:

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

## Documentation Rules

When changing behavior, update:

- `README.md`
- `README-ja.md`
- `docs/DESIGN.md`
- ADRs under `docs/adr/` when decisions change

Do not document local certificate or machine-specific truststore setup.
