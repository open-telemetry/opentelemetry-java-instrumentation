/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.restlet.v2_5;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class RestletInstrumentationModule extends InstrumentationModule {

  private static final Pattern SUPPORTED_VERSION =
      Pattern.compile("2\\.(?:[5-9]|[1-9][0-9]+)\\.[0-9]+(?:-.*)?");

  public RestletInstrumentationModule() {
    super("restlet-2.5", "restlet-2.0", "restlet");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed("org.restlet.Request")
        .and(RestletInstrumentationModule::hasSupportedVersion);
  }

  private static boolean hasSupportedVersion(ClassLoader loader) {
    // Engine.VERSION says "2.5-RC1" in both the release candidate and the 2.5.0 release.
    // Read the published artifact version without loading Restlet classes during matching.
    try (InputStream input =
        loader.getResourceAsStream("META-INF/maven/org.restlet/org.restlet/pom.properties")) {
      if (input == null) {
        return false;
      }
      Properties properties = new Properties();
      properties.load(input);
      String version = properties.getProperty("version", "");
      return SUPPORTED_VERSION.matcher(version).matches() && !version.startsWith("2.5.0-");
    } catch (IOException | IllegalArgumentException ignored) {
      return false;
    }
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new ServerInstrumentation(), new RouteInstrumentation());
  }
}
