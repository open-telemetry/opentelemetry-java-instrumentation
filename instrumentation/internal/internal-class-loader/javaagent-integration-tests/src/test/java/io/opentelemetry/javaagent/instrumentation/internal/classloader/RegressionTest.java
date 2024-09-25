package io.opentelemetry.javaagent.instrumentation.internal.classloader;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class RegressionTest {
  // https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/5155
  // loading a class that is extended/implemented by a helper class causes
  // java.lang.LinkageError: loader 'app' (instance of jdk.internal.loader.ClassLoaders$AppClassLoader)
  // attempted duplicate interface definition for org.apache.commons.lang3.function.FailableCallable
  // this test verifies that the duplicate class definition LinkageError is not thrown into
  // application code
  @Test
  void noDuplicateClassDefinition() throws ClassNotFoundException {
    assertThat(Class.forName("org.apache.commons.lang3.function.FailableCallable")).isNotNull();
  }

}
