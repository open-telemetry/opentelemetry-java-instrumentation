/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import org.junit.jupiter.api.Test;

class StaticImportFormatterTest {

  private final StaticImportFormatter formatter = new StaticImportFormatter();

  @Test
  void preservesQualifiedCallsInSemconvSelectionWrappers() {
    String source =
        "import io.opentelemetry.instrumentation.api.internal.SemconvStability;\n"
            + "class Wrapper {\n"
            + "  public static boolean emitOldRpcSemconv(OpenTelemetry openTelemetry) {\n"
            + "    return SemconvStability.emitOldRpcSemconv(openTelemetry);\n"
            + "  }\n"
            + "}\n";

    assertThat(formatter.applyWithFile(source, new File("Wrapper.java"))).isEqualTo(source);
  }

  @Test
  void staticallyImportsSemconvSelectionInOtherClasses() {
    String source =
        "import io.opentelemetry.instrumentation.api.internal.SemconvStability;\n"
            + "class Caller {\n"
            + "  boolean preview(OpenTelemetry openTelemetry) {\n"
            + "    return SemconvStability.emitPreviewRpcSemconv(openTelemetry);\n"
            + "  }\n"
            + "}\n";

    assertThat(formatter.applyWithFile(source, new File("Caller.java")))
        .contains(
            "import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitPreviewRpcSemconv;",
            "return emitPreviewRpcSemconv(openTelemetry);")
        .doesNotContain("return SemconvStability.emitPreviewRpcSemconv(openTelemetry);");
  }
}
