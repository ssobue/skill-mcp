# Skill MCP

Skill MCPは、部門やチームが管理する複数のGitリポジトリに分散したSkillを検出し、MCP経由で検索・参照できるようにするSpring BootベースのMCPサーバーです。

v1では意図的に小さく始めます。

- `ssobue/demo` の作法に寄せたJava 21 + Spring Boot構成
- package/namespaceは `dev.sobue.ai.skill.mcp`
- JSON処理はSpring Boot管理のJackson 3を使用
- Skill manifestのYAML読み込みはSnakeYAMLを使用
- GitHubリポジトリアクセスはGitHub App認証付きのHub4j GitHub APIを使用
- 認証・認可なし
- データベースなし
- 監査ログなし
- 中央サーバーでSkillのソースコードを実行しない
- ローカルGit/worktreeスキャンと設定済みGitHubリポジトリからメモリ上にカタログを再構築

MCP endpointは `/mcp` です。

## このソフトウェアを作った背景

既存のMCP registryやmanagerは有用ですが、多くは公開MCP serverのメタデータ管理、MCP serverのインストール、gateway、sandbox実行などを主眼にしています。Agent SkillsやClaude Code SkillsはSkill folderの形式や利用方法として重要ですが、部門ごとに管理される複数のGitHubリポジトリをGitHub Appで直接参照し、組織内向けにMCPで検索可能にする小さなカタログサーバーではありません。

Skill MCPは意図的に範囲を絞っています。内部SkillをGitからカタログ化し、Skillの所有権は各部門のリポジトリに残し、personal access tokenを使わず、v1ではデータベースを持たず、中央サーバーではSkillコードを実行しません。

この判断のために比較した製品・OSSは [ADR 0003](docs/adr/0003-custom-skill-catalog.md) に記録しています。

## 起動

```bash
./gradlew test
./gradlew bootRun
```

デフォルトでは現在のリポジトリルートをスキャンします。

```yaml
skill-mcp:
  scan:
    roots:
      - .
    fixed-delay-millis: 300000
```

`skill-mcp.scan.roots` には、チェックアウト済みのSkillリポジトリ、または複数リポジトリを含むディレクトリを指定します。

ローカルにcloneせず、GitHubリポジトリを直接参照することもできます。

```yaml
skill-mcp:
  github:
    app-id: ${GITHUB_APP_ID:}
    installation-id: ${GITHUB_APP_INSTALLATION_ID:}
    private-key-path: ${GITHUB_APP_PRIVATE_KEY_PATH:}
    repositories:
      - url: https://github.com/example/team-skills.git
        ref: main
      - url: git@github.com:example/platform-skills.git
        ref: v1
```

GitHubアクセスはpersonal access tokenではなくGitHub App前提です。対象リポジトリを読むため、GitHub AppにはRepository contentsのread権限を付与します。PEMを環境変数で渡す場合は `GITHUB_APP_PRIVATE_KEY` に `\n` エスケープ付きで設定できます。

## MCPインターフェース

対応メソッド:

- `initialize`
- `resources/list`
- `resources/read`
- `resources/templates/list`
- `tools/list`
- `tools/call`
- `prompts/list`
- `prompts/get`

提供ツール:

- `search_skills`: キーワード、tag、部門、チームでSkillを検索する
- `get_skill_location`: Skillのrepository URL、ref、pathを返す

`search_skills` の例:

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

`resources/read` の例:

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

## Skill管理リポジトリの構成

Skill管理リポジトリは次の構成を推奨します。

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

各Skillは `skills/<skill-id>/` 配下に置きます。必須ファイルは次の2つです。

- `SKILL.md`: 利用者とエージェントが読むSkill指示書
- `skill.yaml`: MCPサーバーが読む機械向けmanifest

任意で以下も置けます。

- `scripts/`: 実行補助スクリプトや小さなツール
- `references/`: 参考資料、例、schema、標準文書
- `assets/`: 画像、テンプレート、静的ファイル

## skill.yaml

必須項目:

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

任意項目:

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

`visibility_groups` は将来の認可用です。v1では認証なしのため、検出されたSkillはすべての利用者に見えます。

`executable` はSkillの実行方法をエージェントへ伝えるためのメタデータです。中央MCPサーバーはコマンドを実行しません。

## 自動構成プロンプト

部門やチームのリポジトリをSkill管理リポジトリにする場合、Codex等に次のプロンプトを渡します。

```text
このGitリポジトリを、中央Skill Registry MCPサーバーに公開するSkill管理リポジトリとして構成してください。

次の構造を作成してください。

- skills/
- skills/example-skill/
- skills/example-skill/SKILL.md
- skills/example-skill/skill.yaml

ルール:

- 各Skillは skills/<skill-id>/ に置く
- 各Skill directoryには SKILL.md と skill.yaml を置く
- skill.yamlには skill_id, name, description, owner_department, owner_team, tags, skill_path を含める
- skill_pathはSkill directoryを指す
- 実行が本当に必要な場合以外はscriptsを追加しない
- 実行コードを追加する場合は、runtime, command, args, inputs, permissions をskill.yamlに定義する

部門名、チーム名、作成したいSkillのテーマが不明な場合は、編集前に質問してください。
```

## ドキュメント

- [設計ドキュメント](docs/DESIGN.md)
- [ADR](docs/adr/0001-v1-architecture.md)
- [独自カタログを作る判断](docs/adr/0003-custom-skill-catalog.md)
- [English README](README.md)
