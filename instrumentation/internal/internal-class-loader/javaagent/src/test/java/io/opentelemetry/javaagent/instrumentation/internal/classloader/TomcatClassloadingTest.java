/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.internal.classloader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.javaagent.bootstrap.HelperResources;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.stream.Collectors;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.WebResource;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.loader.ParallelWebappClassLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class TomcatClassloadingTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private static ParallelWebappClassLoader classloader;

  @BeforeAll
  static void setup() throws LifecycleException {
    WebResourceRoot resources = mock(WebResourceRoot.class);
    WebResource resource = mock(WebResource.class);
    WebResource[] webResources = new WebResource[0];

    when(resources.getResource(any())).thenReturn(resource);
    when(resources.getClassLoaderResource(any())).thenReturn(resource);
    when(resources.listResources(any())).thenReturn(webResources);
    when(resources.getResources(any())).thenReturn(webResources);
    when(resources.getClassLoaderResources(any())).thenReturn(webResources);

    ClassLoader parentClassloader = new ClassLoader(null) {};
    classloader = new ParallelWebappClassLoader(parentClassloader);
    classloader.setResources(resources);
    classloader.init();
    classloader.start();
  }

  @Test
  void testTomcatClassLoadingDelegatesToParentForAgentClasses() throws ClassNotFoundException {
    // If instrumentation didn't work this would blow up with NPE due to incomplete resources
    // mocking
    classloader.loadClass("io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge");
  }

  @Test
  void testResourceInjection() throws IOException {
    Path tmpFile = Files.createTempFile("hello", "tmp");
    tmpFile.toFile().deleteOnExit();

    Files.write(tmpFile, "hello".getBytes(StandardCharsets.UTF_8));
    URL url = tmpFile.toUri().toURL();
    HelperResources.register(classloader, "hello.txt", Collections.singletonList(url));

    assertThat(classloader.getResource("hello.txt")).isNotNull();

    Enumeration<URL> resources = classloader.getResources("hello.txt");

    assertThat(resources).isNotNull();
    assertThat(resources.hasMoreElements()).isTrue();

    InputStream inputStream = classloader.getResourceAsStream("hello.txt");
    cleanup.deferCleanup(inputStream);

    assertThat(inputStream).isNotNull();

    String text =
        new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))
            .lines()
            .collect(Collectors.joining("\n"));

    assertThat(text).isEqualTo("hello");
  }
}
