package dev.sobue.ai.skill.mcp.catalog;

import dev.sobue.ai.skill.mcp.config.SkillMcpProperties;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SkillCatalogService {

  private final SkillMcpProperties properties;
  private final SkillScanner scanner;
  private final GitHubSkillScanner gitHubSkillScanner;
  private final AtomicReference<CatalogSnapshot> snapshot = new AtomicReference<>(CatalogSnapshot.empty());

  @EventListener(ApplicationReadyEvent.class)
  public void refreshOnStartup() {
    refresh();
  }

  @Scheduled(fixedDelayString = "${skill-mcp.scan.fixed-delay-millis:300000}")
  public void refresh() {
    CatalogSnapshot next =
        merge(
            scanner.scan(properties.scan().roots()),
            gitHubSkillScanner.scan(properties.github()));
    snapshot.set(next);
  }

  public CatalogSnapshot snapshot() {
    return snapshot.get();
  }

  public List<SkillEntry> search(String query, String tag, String ownerDepartment, String ownerTeam) {
    String normalizedQuery = normalize(query);
    String normalizedTag = normalize(tag);
    String normalizedDepartment = normalize(ownerDepartment);
    String normalizedTeam = normalize(ownerTeam);
    return snapshot().skills().stream()
        .filter(skill -> matchesQuery(skill, normalizedQuery))
        .filter(skill -> normalizedTag.isEmpty() || skill.tags().stream().anyMatch(t -> normalize(t).equals(normalizedTag)))
        .filter(skill -> normalizedDepartment.isEmpty() || normalize(skill.ownerDepartment()).contains(normalizedDepartment))
        .filter(skill -> normalizedTeam.isEmpty() || normalize(skill.ownerTeam()).contains(normalizedTeam))
        .toList();
  }

  public Optional<SkillEntry> findById(String skillId) {
    return snapshot().skills().stream().filter(skill -> skill.skillId().equals(skillId)).findFirst();
  }

  private boolean matchesQuery(SkillEntry skill, String query) {
    if (query.isEmpty()) {
      return true;
    }
    return normalize(skill.skillId()).contains(query)
        || normalize(skill.name()).contains(query)
        || normalize(skill.description()).contains(query)
        || skill.tags().stream().anyMatch(tag -> normalize(tag).contains(query));
  }

  private String normalize(String value) {
    return value == null ? "" : value.toLowerCase().trim();
  }

  private CatalogSnapshot merge(CatalogSnapshot local, CatalogSnapshot github) {
    List<SkillEntry> skills = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    Set<String> skillIds = new HashSet<>();

    warnings.addAll(local.warnings());
    warnings.addAll(github.warnings());
    for (SkillEntry skill : Stream.concat(local.skills().stream(), github.skills().stream()).toList()) {
      if (skillIds.add(skill.skillId())) {
        skills.add(skill);
      } else {
        warnings.add("Skill skipped because skill_id is duplicated across sources: " + skill.skillId());
      }
    }
    skills.sort(Comparator.comparing(SkillEntry::skillId));
    return new CatalogSnapshot(List.copyOf(skills), List.copyOf(warnings), Instant.now());
  }
}
