package dev.sobue.ai.skill.mcp.catalog;

import dev.sobue.ai.skill.mcp.config.SkillMcpProperties;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.boot.context.event.ApplicationReadyEvent;

@Service
public class SkillCatalogService {

  private final SkillMcpProperties properties;
  private final SkillScanner scanner;
  private final AtomicReference<CatalogSnapshot> snapshot = new AtomicReference<>(CatalogSnapshot.empty());

  public SkillCatalogService(SkillMcpProperties properties, SkillScanner scanner) {
    this.properties = properties;
    this.scanner = scanner;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void refreshOnStartup() {
    refresh();
  }

  @Scheduled(fixedDelayString = "${skill-mcp.scan.fixed-delay-millis:300000}")
  public void refresh() {
    CatalogSnapshot next = scanner.scan(properties.getScan().getRoots());
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
}
