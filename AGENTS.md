# AGENTS.md

## Project

This repository contains Skill MCP, a Spring Boot based MCP catalog server for Skills stored in distributed Git repositories.

## Language and Build

- Use Java 21.
- Follow the lightweight Spring Boot project style used by `https://github.com/ssobue/demo`.
- The base package is `dev.sobue.ai.skill.mcp`.
- Use Gradle Wrapper for builds.
- Use Spring Boot managed Jackson 3 for JSON.
- Use SnakeYAML for `skill.yaml`; do not add Jackson YAML for manifest parsing.
- Run tests with:

```bash
./gradlew test
```

If a build fails because of Java version differences, verify with more than one installed JDK. Use `/usr/libexec/java_home` to switch `JAVA_HOME`.

Example:

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 25)"
```

## Architecture Rules

- Keep Git as the source of truth for Skill definitions.
- Do not introduce a database in v1.
- Do not add authentication or authorization in v1.
- Do not add audit logging in v1.
- Do not execute Skill source code on the central server.
- Treat executable Skill metadata as instructions for user-side agents.
- Keep MCP response shapes compatible with JSON-RPC MCP clients.

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
