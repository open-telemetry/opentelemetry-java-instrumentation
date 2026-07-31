package io.opentelemetry.instrumentation.jmx.internal;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class JmxTelemetryRulesTest {

  @Test
  public void testGetSupportedSystems() throws IOException {
    assertThat(JmxTelemetryRules.getSupportedSystems())
        .containsExactlyInAnyOrder(
        "activemq",
        "camel",
        "experimental-cassandra",
        "experimental-kafka-connect",
        "hadoop",
        "jetty",
        "jvm",
        "tomcat",
        "wildfly"
    );

    Set<String> allRules = new HashSet<>();
    for (String system : JmxTelemetryRules.getSupportedSystems()) {
      Set<String> rulesForSystem = JmxTelemetryRules.locateRulesForSystem(
          JmxTelemetryRulesTest.class.getClassLoader(), system, true);
      assertThat(rulesForSystem)
          .describedAs("at leat one rule file should be used for system %s", system)
          .isNotEmpty();
      allRules.addAll(rulesForSystem);
    }

    assertThat(allRules)
        .containsExactlyInAnyOrderElementsOf(getYamlFilesFromFileSystem());

  }

  Set<String> getYamlFilesFromFileSystem() throws IOException {
    Path rulesRoot = getYamlRootFolder();
    try (Stream<Path> stream = Files.walk(rulesRoot)){
      return stream
          .filter(Files::isRegularFile)
          .map(path -> Paths.get("jmx", "rules").resolve(rulesRoot.relativize(path)))
          .map(Path::toString)
          .collect(Collectors.toSet());
    }
  }


  private static Path getYamlRootFolder(){
    URL resource = JmxTelemetryRulesTest.class.getClassLoader()
        .getResource(JmxTelemetryRules.class.getName().replace(".", "/") + ".class");
    assertThat(resource).isNotNull();
    Path path = Paths.get(resource.getPath());
    while(path != null && !path.equals(path.getRoot()) && !path.endsWith("library")){
      path = path.getParent();
    }
    assertThat(path).isNotNull();
    return path.resolve(Paths.get("src", "main", "resources", "jmx", "rules"));
  }
}
