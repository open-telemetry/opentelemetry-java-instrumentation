/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.resources;

import static io.opentelemetry.semconv.ServiceAttributes.SERVICE_VERSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.resources.Resource;
import java.io.InputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SpringBootServiceVersionDetectorTest {

  static final String BUILD_PROPS = "build-info.properties";
  static final String META_INFO = "META-INF";

  @Mock ConfigProperties config;
  @Mock SystemHelper system;

  @Test
  void givenBuildVersionIsPresentInBuildInfProperties_thenReturnBuildVersion() {
    when(system.openClasspathResource(META_INFO, BUILD_PROPS))
        .thenReturn(openClasspathResource(META_INFO + "/" + BUILD_PROPS));

    SpringBootServiceVersionDetector guesser = new SpringBootServiceVersionDetector(system);
    Resource result = guesser.createResource(config);
    assertThat(result.getAttribute(SERVICE_VERSION)).isEqualTo("0.0.2");
  }

  @Test
  void givenBuildVersionFileNotPresent_thenReturnEmptyResource() {
    when(system.openClasspathResource(META_INFO, BUILD_PROPS)).thenReturn(null);

    SpringBootServiceVersionDetector guesser = new SpringBootServiceVersionDetector(system);
    Resource result = guesser.createResource(config);
    assertThat(result).isEqualTo(Resource.empty());
  }

  @Test
  void givenBuildVersionFileIsPresentButBuildVersionPropertyNotPresent_thenReturnEmptyResource() {
    when(system.openClasspathResource(META_INFO, BUILD_PROPS))
        .thenReturn(openClasspathResource(BUILD_PROPS));

    SpringBootServiceVersionDetector guesser = new SpringBootServiceVersionDetector(system);
    Resource result = guesser.createResource(config);
    assertThat(result).isEqualTo(Resource.empty());
  }

  private InputStream openClasspathResource(String resource) {
    return getClass().getClassLoader().getResourceAsStream(resource);
  }
}
