package dev.sobue.ai.skill.mcp.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SkillScannerTests {

  private final SkillScanner scanner = new SkillScanner();

  @TempDir Path tempDir;

  @Test
  void scansValidSkillManifest() throws IOException {
    Path skillDir = tempDir.resolve("skills/example-skill");
    Files.createDirectories(skillDir);
    Files.writeString(skillDir.resolve("SKILL.md"), "# Example Skill\n");
    Files.writeString(
        skillDir.resolve("skill.yaml"),
        """
        skill_id: example-skill
        name: Example Skill
        description: Helps test catalog scanning.
        owner_department: Engineering
        owner_team: Platform
        tags:
          - test
        skill_path: skills/example-skill
        """);

    CatalogSnapshot snapshot = scanner.scan(List.of(tempDir));

    assertThat(snapshot.warnings()).isEmpty();
    assertThat(snapshot.skills()).hasSize(1);
    SkillEntry skill = snapshot.skills().getFirst();
    assertThat(skill.skillId()).isEqualTo("example-skill");
    assertThat(skill.name()).isEqualTo("Example Skill");
    assertThat(skill.skillPath()).isEqualTo("skills/example-skill");
  }

  @Test
  void parsesOptionalManifestFields() {
    SkillManifest manifest =
        scanner.readSkillManifest(
            """
            skill_id: executable-skill
            name: Executable Skill
            description: Has executable metadata.
            owner_department: Engineering
            owner_team: Platform
            tags: test
            skill_path: skills/executable-skill
            visibility_groups:
              - platform
            executable:
              type: command
              runtime: shell
              working_directory: .
              command: ./scripts/run.sh
              args:
                - --verbose
              inputs:
                - name: input
                  type: file
              permissions:
                filesystem: workspace
                network: false
            """);

    assertThat(manifest.tags()).containsExactly("test");
    assertThat(manifest.visibilityGroups()).containsExactly("platform");
    assertThat(manifest.executable().type()).isEqualTo("command");
    assertThat(manifest.executable().args()).containsExactly("--verbose");
    assertThat(manifest.executable().inputs()).hasSize(1);
    assertThat(manifest.executable().permissions()).containsEntry("filesystem", "workspace");
  }

  @Test
  void skipsManifestWhenSkillMarkdownIsMissing() throws IOException {
    Path skillDir = tempDir.resolve("skills/broken-skill");
    Files.createDirectories(skillDir);
    Files.writeString(
        skillDir.resolve("skill.yaml"),
        """
        skill_id: broken-skill
        name: Broken Skill
        description: Missing SKILL.md.
        owner_department: Engineering
        owner_team: Platform
        tags:
          - test
        skill_path: skills/broken-skill
        """);

    CatalogSnapshot snapshot = scanner.scan(List.of(tempDir));

    assertThat(snapshot.skills()).isEmpty();
    assertThat(snapshot.warnings()).anyMatch(warning -> warning.contains("SKILL.md is missing"));
  }

  @Test
  void reportsMissingScanRoot() {
    Path missingRoot = tempDir.resolve("missing");

    CatalogSnapshot snapshot = scanner.scan(List.of(missingRoot));

    assertThat(snapshot.skills()).isEmpty();
    assertThat(snapshot.warnings()).containsExactly("Scan root does not exist: " + missingRoot.normalize());
  }

  @Test
  void skipsInvalidManifest() throws IOException {
    Path skillDir = tempDir.resolve("skills/invalid-skill");
    Files.createDirectories(skillDir);
    Files.writeString(skillDir.resolve("SKILL.md"), "# Invalid Skill\n");
    Files.writeString(
        skillDir.resolve("skill.yaml"),
        """
        skill_id: invalid-skill
        name: Invalid Skill
        """);

    CatalogSnapshot snapshot = scanner.scan(List.of(tempDir));

    assertThat(snapshot.skills()).isEmpty();
    assertThat(snapshot.warnings()).anyMatch(warning -> warning.contains("is invalid"));
  }

  @Test
  void skipsDuplicateSkillIds() throws IOException {
    writeSkill("skills/first", "duplicate-skill", "First Skill");
    writeSkill("skills/second", "duplicate-skill", "Second Skill");

    CatalogSnapshot snapshot = scanner.scan(List.of(tempDir));

    assertThat(snapshot.skills()).hasSize(1);
    assertThat(snapshot.warnings()).anyMatch(warning -> warning.contains("duplicated"));
  }

  private void writeSkill(String path, String skillId, String name) throws IOException {
    Path skillDir = tempDir.resolve(path);
    Files.createDirectories(skillDir);
    Files.writeString(skillDir.resolve("SKILL.md"), "# " + name + "\n");
    Files.writeString(
        skillDir.resolve("skill.yaml"),
        """
        skill_id: %s
        name: %s
        description: Test Skill.
        owner_department: Engineering
        owner_team: Platform
        tags:
          - test
        skill_path: %s
        """
            .formatted(skillId, name, path));
  }
}
