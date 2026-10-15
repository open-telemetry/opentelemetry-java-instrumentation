/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.instrumentation.docs.internal.InstrumentationModule;
import io.opentelemetry.instrumentation.docs.parsers.ModuleParser;
import io.opentelemetry.instrumentation.docs.utils.InstrumentationPath;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModuleConverterTest {

  @Test
  void testConvertToModulesCreatesUniqueModules() {
    InstrumentationPath path1 = mock(InstrumentationPath.class);
    when(path1.group()).thenReturn("g1");
    when(path1.namespace()).thenReturn("n1");
    when(path1.instrumentationName()).thenReturn("i1");
    when(path1.srcPath()).thenReturn("instrumentation/foo");

    InstrumentationPath path2 = mock(InstrumentationPath.class);
    when(path2.group()).thenReturn("g1");
    when(path2.namespace()).thenReturn("n1");
    when(path2.instrumentationName()).thenReturn("i1");
    when(path2.srcPath()).thenReturn("instrumentation/foo");

    InstrumentationPath path3 = mock(InstrumentationPath.class);
    when(path3.group()).thenReturn("g2");
    when(path3.namespace()).thenReturn("n2");
    when(path3.instrumentationName()).thenReturn("i2");
    when(path3.srcPath()).thenReturn("instrumentation/bar");

    List<InstrumentationModule> modules =
        ModuleParser.convertToModules(asList(path1, path2, path3));

    assertThat(modules)
        .extracting(InstrumentationModule::getGroup)
        .containsExactlyInAnyOrder("g1", "g2");
  }

  @Test
  void testConvertToModulesKeepsSrcPath() {
    InstrumentationPath path = mock(InstrumentationPath.class);
    when(path.group()).thenReturn("camel");
    when(path.namespace()).thenReturn("camel");
    when(path.instrumentationName()).thenReturn("camel-2.20");
    when(path.srcPath()).thenReturn("instrumentation/camel-2.20");

    List<InstrumentationModule> modules = ModuleParser.convertToModules(singletonList(path));

    assertThat(modules)
        .extracting(InstrumentationModule::getSrcPath)
        .containsExactly("instrumentation/camel-2.20");
  }
}
