/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.resources;

import com.google.auto.service.AutoService;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.ResourceAttributes;

/**
 * Detects <code>service.name</code> and <code>service.version</code> from Spring Boot's <code>
 * build-info.properties</code> file.
 *
 * <p>Use the following snippet in your pom.xml file to generate the build-info.properties file:
 *
 * <pre>{@code
 * <build>
 *     <finalName>${project.artifactId}</finalName>
 *     <plugins>
 *         <plugin>
 *             <groupId>org.springframework.boot</groupId>
 *             <artifactId>spring-boot-maven-plugin</artifactId>
 *             <executions>
 *                 <execution>
 *                     <goals>
 *                         <goal>build-info</goal>
 *                         <goal>repackage</goal>
 *                     </goals>
 *                 </execution>
 *             </executions>
 *         </plugin>
 *     </plugins>
 * </build>
 * }</pre>
 *
 * <p>Use the following snippet in your gradle file to generate the build-info.properties file:
 *
 * <pre>{@code
 * springBoot {
 *   buildInfo {
 *   }
 * }
 * }</pre>
 *
 * <p>Note: The spring starter already includes provider in
 * io.opentelemetry.instrumentation.spring.autoconfigure.resources.SpringResourceProvider
 */
@AutoService(ResourceProvider.class)
public class SpringBootServiceVersionDetector extends SpringBootBuildInfoDetector {

  public SpringBootServiceVersionDetector() {
    this(new SystemHelper());
  }

  // Exists for testing
  SpringBootServiceVersionDetector(SystemHelper system) {
    super(ResourceAttributes.SERVICE_VERSION, "build.version", system);
  }

  @Override
  public boolean shouldApply(ConfigProperties config, Resource existing) {
    return !config
            .getMap("otel.resource.attributes")
            .containsKey(ResourceAttributes.SERVICE_VERSION.getKey())
        && existing.getAttribute(ResourceAttributes.SERVICE_VERSION) == null;
  }
}
