package dev.sobue.ai.skill.mcp;

import dev.sobue.ai.skill.mcp.config.SkillMcpProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan(basePackageClasses = SkillMcpProperties.class)
@EnableScheduling
public class SkillMcpApplication {

  public static void main(String[] args) {
    SpringApplication.run(SkillMcpApplication.class, args);
  }
}
