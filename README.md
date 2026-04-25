# Skill MCP

Skill MCP is a Spring Boot based MCP server for discovering Skills that are distributed across department- or team-managed Git repositories.

The server is intentionally small for v1:

- Java 21 and Spring Boot, following the baseline style of `ssobue/demo`.
- Package namespace: `dev.sobue.ai.skill.mcp`.
- JSON support uses Spring Boot managed Jackson 3.
- Skill manifest YAML parsing uses SnakeYAML.
- No authentication or authorization.
- No database.
- No audit log.
- No central execution of Skill source code.
- In-memory catalog rebuilt from Git/worktree scans.

The server exposes a JSON-RPC MCP endpoint at `/mcp`.

## Quick Start

```bash
./gradlew test
./gradlew bootRun
```

By default, the server scans the current repository root:

```yaml
skill-mcp:
  scan:
    roots:
      - .
    fixed-delay-millis: 300000
```

Point `skill-mcp.scan.roots` at one or more checked-out Skill repositories or directories containing checked-out repositories.

## MCP Interface

Supported methods:

- `initialize`
- `resources/list`
- `resources/read`
- `resources/templates/list`
- `tools/list`
- `tools/call`
- `prompts/list`
- `prompts/get`

Available tools:

- `search_skills`: search Skills by keyword, tag, department, or team.
- `get_skill_location`: return repository URL, ref, and Skill path for a Skill.

Example `search_skills` call:

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/call",
  "params": {
    "name": "search_skills",
    "arguments": {
      "query": "spring",
      "tag": "java"
    }
  }
}
```

Example `resources/read` call:

```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "method": "resources/read",
  "params": {
    "uri": "skill://spring-api-standards"
  }
}
```

## Skill Repository Bootstrap Guide

This repository describes how to structure a Git repository that publishes Skills to a central Skill Registry MCP server.

The central MCP server does not own Skill source code. Each department or team keeps its Skills in its own Git repository. The MCP server discovers valid Skill definitions, indexes their metadata, and exposes them to MCP clients as searchable resources and tools.

In v1, the MCP server is a catalog only:

- It discovers Skill metadata from Git repositories.
- It exposes searchable Skill locations through MCP.
- It does not authenticate users.
- It does not use a database.
- It does not execute Skill source code.
- It does not install Skills into user environments.

Executable Skills are fetched and run by the user's agent or local workspace, according to that environment's normal approval and execution policy.

## Repository Layout

A Skill repository should use this layout:

```text
.
├── skills/
│   ├── spring-api-standards/
│   │   ├── SKILL.md
│   │   └── skill.yaml
│   └── openapi-generator/
│       ├── SKILL.md
│       ├── skill.yaml
│       └── scripts/
│           └── generate.sh
└── README.md
```

Each Skill is a directory under `skills/`. A valid Skill directory must contain:

- `SKILL.md`: the human- and agent-readable Skill instructions.
- `skill.yaml`: the machine-readable manifest used by the central MCP server.

Optional directories may be added when useful:

- `scripts/`: executable helper scripts or small tools.
- `references/`: reference documents, examples, schemas, or style guides.
- `assets/`: images, templates, or other static files.

## Skill Manifest

Each Skill must define a `skill.yaml` file.

Required fields:

```yaml
skill_id: spring-api-standards
name: Spring API Standards
description: Standards for designing and implementing Spring Boot REST APIs.
owner_department: Platform Engineering
owner_team: API Enablement
tags:
  - spring-boot
  - rest-api
  - java
skill_path: skills/spring-api-standards
```

Optional fields:

```yaml
visibility_groups:
  - platform-engineering
  - backend-developers

executable:
  type: command
  runtime: shell
  working_directory: .
  command: ./scripts/generate.sh
  args:
    - "--input"
    - "${input_file}"
  inputs:
    - name: input_file
      type: file
      required: true
      description: Input file consumed by the Skill script.
  permissions:
    filesystem: workspace
    network: false
```

`visibility_groups` is reserved for future authorization support. In the current unauthenticated v1 design, all discovered Skills are visible to all users of the MCP server.

`executable` tells agents how a Skill can be run. The central MCP server only catalogs this metadata. It does not execute the command.

## Discovery Rules

The central MCP server should publish a Skill when all of these conditions are true:

- The repository is inside a configured GitHub organization or GitLab group.
- A `skill.yaml` file exists under a Skill directory.
- The manifest contains all required fields.
- `skill_path` points to the Skill directory.
- `SKILL.md` exists in the declared Skill directory.
- `skill_id` is unique across all discovered Skills.

Invalid Skills are skipped until the repository is fixed.

## Minimal Skill Example

`skills/spring-api-standards/skill.yaml`:

```yaml
skill_id: spring-api-standards
name: Spring API Standards
description: Apply team standards when designing and implementing Spring Boot REST APIs.
owner_department: Platform Engineering
owner_team: API Enablement
tags:
  - spring-boot
  - rest-api
  - java
skill_path: skills/spring-api-standards
```

`skills/spring-api-standards/SKILL.md`:

```markdown
# Spring API Standards

Use this Skill when designing, implementing, or reviewing Spring Boot REST APIs.

## Instructions

- Follow resource-oriented REST endpoint naming.
- Use request and response DTOs instead of exposing persistence entities.
- Return structured error responses.
- Add validation for external inputs.
- Include focused tests for controller and service behavior.

## Output

When applying this Skill, produce implementation notes, code changes, and test recommendations that follow these standards.
```

## Executable Skill Example

`skills/openapi-generator/skill.yaml`:

```yaml
skill_id: openapi-generator
name: OpenAPI Generator Helper
description: Generate a starter Spring controller and DTOs from an OpenAPI document.
owner_department: Platform Engineering
owner_team: API Enablement
tags:
  - openapi
  - spring-boot
  - code-generation
skill_path: skills/openapi-generator

executable:
  type: command
  runtime: shell
  working_directory: .
  command: ./scripts/generate.sh
  args:
    - "${openapi_file}"
  inputs:
    - name: openapi_file
      type: file
      required: true
      description: Path to the OpenAPI YAML or JSON document.
  permissions:
    filesystem: workspace
    network: false
```

`skills/openapi-generator/SKILL.md`:

```markdown
# OpenAPI Generator Helper

Use this Skill when a user wants to generate starter Spring Boot API code from an OpenAPI document.

## Instructions

1. Inspect the OpenAPI document.
2. Confirm the target package and output directory.
3. Run the executable only after user approval.
4. Review generated files before presenting them as final work.
5. Add or recommend tests for generated controller behavior.

## Execution

This Skill includes a local script declared in `skill.yaml`.
The central MCP server does not run it. The user's agent or workspace is responsible for fetching the Skill and executing it locally.
```

## Bootstrap Prompt

Use this prompt with Codex or another coding agent to convert a repository into a Skill repository.

```text
You are configuring this Git repository as a Skill repository for a central Skill Registry MCP server.

Create the following structure:

- skills/
- skills/example-skill/
- skills/example-skill/SKILL.md
- skills/example-skill/skill.yaml

The repository must follow these rules:

- Each Skill lives under skills/<skill-id>/.
- Each Skill directory must contain SKILL.md and skill.yaml.
- skill.yaml must include skill_id, name, description, owner_department, owner_team, tags, and skill_path.
- skill_path must point to the Skill directory.
- Do not add executable scripts unless the Skill genuinely needs code execution.
- If executable scripts are added, declare runtime, command, args, inputs, and permissions in skill.yaml.

Create a useful example Skill for this team. Ask me for the department name, team name, and intended Skill topic if they are not already clear from the repository.
```

## Skill Authoring Prompt

Use this prompt when adding a new Skill to an existing Skill repository.

```text
Add a new Skill to this repository.

Before editing files, determine:

- the Skill ID
- the Skill name
- the problem it solves
- the intended users
- the owner department
- the owner team
- useful tags
- whether the Skill needs executable code

Then create:

- skills/<skill-id>/SKILL.md
- skills/<skill-id>/skill.yaml

The SKILL.md must contain clear instructions that an agent can follow.
The skill.yaml must be valid for the central Skill Registry MCP server.

If executable code is needed, place it under scripts/ inside the Skill directory and add an executable block to skill.yaml. The executable must be designed to run in the user's workspace, not on the central MCP server.
```

## Review Prompt

Use this prompt to review a Skill repository before publishing or merging changes.

```text
Review this repository as a Skill repository for a central Skill Registry MCP server.

Check the following:

- every Skill is under skills/<skill-id>/
- every Skill has SKILL.md
- every Skill has skill.yaml
- every skill.yaml contains all required fields
- every skill_path matches the actual directory
- skill_id values are unique
- tags are useful and consistent
- executable Skills declare runtime, command, args, inputs, and permissions
- executable Skills do not assume they run on the central MCP server

Report findings first, ordered by severity, with file paths and line numbers when possible.
Then provide a short summary of recommended fixes.
```

## Central MCP Server Behavior

When the central MCP server scans this repository, it should expose discovered Skills through:

- `resources/list`: list available Skills.
- `resources/read`: read metadata for `skill://<skill-id>`.
- `search_skills`: search by keyword, tag, department, or team.
- `get_skill_location`: return the Git repository URL, ref, and Skill path.

The client or agent is responsible for fetching the Skill content and deciding whether to run any executable code.

## Notes for Skill Authors

- Keep Skills focused on one repeatable task or workflow.
- Prefer clear instructions in `SKILL.md` over hidden behavior in scripts.
- Use executable code only when it materially improves reliability or repeatability.
- Make scripts safe by default and explicit about required inputs.
- Do not include secrets, credentials, personal data, or environment-specific tokens.
- Treat `skill_id` as stable once published.

## Documentation

- [Design document](docs/DESIGN.md)
- [Architecture decisions](docs/adr/0001-v1-architecture.md)
- [Japanese README](README-ja.md)
