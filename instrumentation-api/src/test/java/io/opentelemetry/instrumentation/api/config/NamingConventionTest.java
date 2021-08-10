package io.opentelemetry.instrumentation.api.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class NamingConventionTest {

  @Test
  void normalizeEnvWithHyphen() {
    // Unlikely that the environment can contain a name like this, but let's doc the behavior
    String result = NamingConvention.ENV_VAR.normalize("FOO_BAR-BAZ");
    assertEquals("foo.bar-baz", result);
  }

  @Test
  void systemPropertyWithHyphen() {
    // Normalizes to the same thing
    String result1 = NamingConvention.ENV_VAR.normalize("OTEL_INSTRUMENTATION_COMMON_DB_STATEMENT_SANITIZER_ENABLED");
    String result2 = NamingConvention.DOT.normalize("otel.instrumentation.common.db-statement-sanitizer.enabled");
    assertEquals("otel.instrumentation.common.db.statement.sanitizer.enabled", result1);
    assertEquals("otel.instrumentation.common.db.statement.sanitizer.enabled", result2);
  }

}
