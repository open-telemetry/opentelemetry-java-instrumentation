/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.net.URL;
import java.net.URLClassLoader;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.SystemUtils;
import org.junit.jupiter.api.Test;

class AddUrlTest {

  @Test
  void testShouldInstrumentClassAfterItIsLoadedViaAddUrl() throws Exception {
    TestUrlClassLoader loader = new TestUrlClassLoader();

    // this is just to verify the assumption that TestURLClassLoader is not finding SystemUtils via
    // the test class path (in which case the verification below would not be very meaningful)
    Throwable thrown =
        catchThrowable(
            () -> {
              loader.loadClass(SystemUtils.class.getName());
            });

    assertThat(thrown).isInstanceOf(ClassNotFoundException.class);

    // loading a class in the URLClassLoader in order to trigger
    // a negative cache hit on org.apache.commons.lang3.SystemUtils
    loader.addURL(IOUtils.class.getProtectionDomain().getCodeSource().getLocation());
    loader.loadClass(IOUtils.class.getName());

    loader.addURL(SystemUtils.class.getProtectionDomain().getCodeSource().getLocation());
    Class<?> clazz = loader.loadClass(SystemUtils.class.getName());

    assertThat(clazz.getClassLoader()).isEqualTo(loader);
    assertThat(clazz.getMethod("getHostName").invoke(null)).isEqualTo("not-the-host-name");
  }

  static class TestUrlClassLoader extends URLClassLoader {

    TestUrlClassLoader() {
      super(new URL[0], null);
    }

    // silence CodeNarc. URLClassLoader#addURL is protected, this method is public
    @SuppressWarnings("UnnecessaryOverridingMethod")
    @Override
    public void addURL(URL url) {
      super.addURL(url);
    }
  }
}
