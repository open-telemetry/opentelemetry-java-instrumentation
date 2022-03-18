/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.SystemUtils

class AddUrlTest extends AgentInstrumentationSpecification {

  def "should instrument class after it is loaded via addURL"() {
    given:
    TestURLClassLoader loader = new TestURLClassLoader()

    when:
    // this is just to verify the assumption that TestURLClassLoader is not finding SystemUtils via
    // the test class path (in which case the verification below would not be very meaningful)
    loader.loadClass(SystemUtils.getName())

    then:
    thrown ClassNotFoundException

    when:
    // loading a class in the URLClassLoader in order to trigger
    // a negative cache hit on org.apache.commons.lang3.SystemUtils
    loader.addURL(IOUtils.getProtectionDomain().getCodeSource().getLocation())
    loader.loadClass(IOUtils.getName())

    loader.addURL(SystemUtils.getProtectionDomain().getCodeSource().getLocation())
    def clazz = loader.loadClass(SystemUtils.getName())

    then:
    clazz.getClassLoader() == loader
    clazz.getMethod("getHostName").invoke(null) == "not-the-host-name"
  }

  static class TestURLClassLoader extends URLClassLoader {

    TestURLClassLoader() {
      super(new URL[0], (ClassLoader) null)
    }

    // silence CodeNarc. URLClassLoader#addURL is protected, this method is public
    @SuppressWarnings("UnnecessaryOverridingMethod")
    @Override
    void addURL(URL url) {
      super.addURL(url)
    }
  }
}
