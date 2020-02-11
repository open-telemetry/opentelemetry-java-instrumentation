package com.datadog.profiling.controller.openjdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class JfpUtilsTest {

  private static final String CONFIG_ENTRY = "jdk.ThreadAllocationStatistics#enabled";
  private static final String CONFIG_OVERRIDE_ENTRY = "test.continuous.override#value";

  static final String OVERRIDES =
      OpenJdkControllerTest.class.getClassLoader().getResource("overrides.jfp").getFile();

  @Test
  public void testLoadingContinuousConfig() throws IOException {
    final Map<String, String> config = JfpUtils.readNamedJfpResource(OpenJdkController.JFP, null);
    assertEquals("true", config.get(CONFIG_ENTRY));
    assertNull(config.get(CONFIG_OVERRIDE_ENTRY));
  }

  @Test
  public void testLoadingContinuousConfigWithOverride() throws IOException {
    final Map<String, String> config =
        JfpUtils.readNamedJfpResource(OpenJdkController.JFP, OVERRIDES);
    assertEquals("true", config.get(CONFIG_ENTRY));
    assertEquals("200", config.get(CONFIG_OVERRIDE_ENTRY));
  }
}
