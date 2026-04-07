/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.internal.urlclassloader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URL;
import java.net.URLClassLoader;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.SystemUtils;
import org.junit.jupiter.api.Test;

class AddUrlTest {

  @Test
  void testShouldInstrumentClassAfterItIsLoadedViaAddUrl() throws Exception {
    TestUrlClassLoader loader = new TestUrlClassLoader();

    // this is just to verify the assumption that TestUrlClassLoader is not finding SystemUtils via
    // the test classpath (in which case the verification below would not be very meaningful)
    assertThatThrownBy(() -> loader.loadClass(SystemUtils.class.getName()))
        .isInstanceOf(ClassNotFoundException.class);

    // loading a class in the URLClassLoader in order to trigger
    // a negative cache hit on org.apache.commons.lang3.SystemUtils
    loader.addURL(IOUtils.class.getProtectionDomain().getCodeSource().getLocation());
    loader.loadClass(IOUtils.class.getName());

    loader.addURL(SystemUtils.class.getProtectionDomain().getCodeSource().getLocation());
    Class<?> clazz = loader.loadClass(SystemUtils.class.getName());

    assertThat(clazz.getClassLoader()).isEqualTo(loader);
    assertThat(clazz.getMethod("getHostName").invoke(null)).isEqualTo("not-the-host-name");
  }

  private static class TestUrlClassLoader extends URLClassLoader {

    TestUrlClassLoader() {
      super(new URL[0], null);
    }

    @Override
    public void addURL(URL url) {
      super.addURL(url);
    }
  }
}
