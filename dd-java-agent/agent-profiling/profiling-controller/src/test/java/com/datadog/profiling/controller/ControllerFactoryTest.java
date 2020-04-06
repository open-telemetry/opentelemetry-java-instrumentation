package com.datadog.profiling.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.condition.JRE.JAVA_8;

import datadog.trace.api.Config;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnJre;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Note: some additional tests for this class are located in profiling-controller-openjdk module */
@ExtendWith(MockitoExtension.class)
public class ControllerFactoryTest {

  @Mock private Config config;

  @Test
  @EnabledOnJre({JAVA_8})
  public void testCreateControllerJava8() {
    UnsupportedEnvironmentException unsupportedEnvironmentException =
        assertThrows(
            UnsupportedEnvironmentException.class,
            () -> {
              ControllerFactory.createController(config);
            });
    String expected =
        "The JFR controller could not find a supported JFR API, use OpenJDK 11+ or Azul zulu version 1.8.0_212+";
    if ("Azul Systems, Inc.".equals(System.getProperty("java.vendor"))) {
      expected =
          "The JFR controller could not find a supported JFR API, use Azul zulu version 1.8.0_212+";
    }
    assertEquals(expected, unsupportedEnvironmentException.getMessage());
  }
}
