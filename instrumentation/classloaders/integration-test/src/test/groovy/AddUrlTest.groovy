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

    // need to load a class in the URLClassLoader in order to trigger
    // a negative cache hit on org.apache.commons.lang3.SystemUtils
    loader.addURL(IOUtils.class.getProtectionDomain().getCodeSource().getLocation())
    loader.loadClass(IOUtils.class.getName())

    when:
    loader.addURL(SystemUtils.class.getProtectionDomain().getCodeSource().getLocation())
    def clazz = loader.loadClass(SystemUtils.class.getName())

    then:
    clazz.getClassLoader() == loader
    clazz.getMethod("getHostName").invoke(null) == "not-the-host-name"
  }

  static class TestURLClassLoader extends URLClassLoader {

    TestURLClassLoader() {
      super(new URL[0], (ClassLoader) null)
    }

    // overridden to make public
    @Override
    void addURL(URL url) {
      super.addURL(url)
    }
  }
}
