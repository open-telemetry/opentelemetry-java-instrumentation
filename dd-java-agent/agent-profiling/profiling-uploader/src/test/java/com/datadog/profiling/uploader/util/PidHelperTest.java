package com.datadog.profiling.uploader.util;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import org.junit.jupiter.api.Test;

public class PidHelperTest {

  @Test
  public void testPid() throws IOException {
    assertTrue(
        PidHelper.PID > 0,
        "Expect PID to be present since we run tests in systems where we can load it");
  }
}
