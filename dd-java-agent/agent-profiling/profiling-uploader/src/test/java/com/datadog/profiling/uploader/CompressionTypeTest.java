package com.datadog.profiling.uploader;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class CompressionTypeTest {
  @Test
  void testDefault() {
    assertEquals(CompressionType.UNKNOWN, CompressionType.of(""));
    assertEquals(CompressionType.UNKNOWN, CompressionType.of(null));
  }

  @ParameterizedTest
  @EnumSource(CompressionType.class)
  void testOn(final CompressionType type) {
    for (final String checkType : permutateCase(type.name())) {
      assertEquals(type, CompressionType.of(checkType));
    }
  }

  private static List<String> permutateCase(String input) {
    final List<String> output = new ArrayList<>();
    input = input.toLowerCase();
    // fast track for all-upper and all-lower
    output.add(input);
    output.add(input.toUpperCase());

    // use bit operations to generate permutations
    long mask = 0L;
    for (int i = 0; i < input.length(); i++) {
      final StringBuilder sb = new StringBuilder();
      mask += 1;
      long check = mask;
      for (int j = 0; j < input.length(); j++) {
        sb.append(
            ((check & 0x1) == 0x1) ? Character.toUpperCase(input.charAt(j)) : input.charAt(j));
        check = check >> 1;
      }
      output.add(sb.toString());
    }
    return output;
  }
}
