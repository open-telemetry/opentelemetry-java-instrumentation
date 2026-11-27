/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.restlet.v2_5;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.instrumentation.api.internal.cache.Cache;
import io.opentelemetry.javaagent.bootstrap.internal.ClassLoaderMatcherCacheHolder;
import io.opentelemetry.javaagent.bootstrap.internal.InClassLoaderMatcher;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class RestletInstrumentationModule extends InstrumentationModule {

  private static final Pattern SUPPORTED_VERSION =
      Pattern.compile("2\\.(?:[5-9]|[1-9][0-9]+)\\.[0-9]+(?:-.*)?");
  private static final Cache<ClassLoader, Boolean> supportedLoaders = Cache.weak();

  static {
    ClassLoaderMatcherCacheHolder.addCache(supportedLoaders);
  }

  public RestletInstrumentationModule() {
    super("restlet-2.5", "restlet-2.0", "restlet");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed("org.restlet.Request")
        .and(loader ->
            supportedLoaders.computeIfAbsent(
                loader, RestletInstrumentationModule::hasSupportedVersion));
  }

  private static boolean hasSupportedVersion(ClassLoader loader) {
    boolean wasInMatcher = InClassLoaderMatcher.getAndSet(true);
    try {
      return readVersionMetadata(loader);
    } finally {
      InClassLoaderMatcher.set(wasInMatcher);
    }
  }

  private static boolean readVersionMetadata(ClassLoader loader) {
    // Engine.VERSION says "2.5-RC1" in both the release candidate and the 2.5.0 release.
    // Read version metadata from the same artifact as Request without loading the class.
    URL requestClass = loader.getResource("org/restlet/Request.class");
    if (requestClass == null) {
      return false;
    }

    try (InputStream input =
        openMetadata(requestClass, "maven/org.restlet/org.restlet/pom.properties")) {
      Properties properties = new Properties();
      properties.load(input);
      return isSupportedVersion(properties.getProperty("version"));
    } catch (IOException ignored) {
      // Some packaging removes the Maven descriptor but retains the Restlet bundle manifest.
    } catch (IllegalArgumentException ignored) {
      return false;
    }

    try (InputStream input = openMetadata(requestClass, "MANIFEST.MF")) {
      Attributes attributes = new Manifest(input).getMainAttributes();
      return "org.restlet".equals(attributes.getValue("Bundle-SymbolicName"))
          && isSupportedVersion(attributes.getValue("Bundle-Version"));
    } catch (IOException ignored) {
      return false;
    }
  }

  private static InputStream openMetadata(URL requestClass, String path) throws IOException {
    URLConnection connection = new URL(requestClass, "../../META-INF/" + path).openConnection();
    connection.setUseCaches(false);
    return connection.getInputStream();
  }

  private static boolean isSupportedVersion(@Nullable String version) {
    return version != null
        && SUPPORTED_VERSION.matcher(version).matches()
        && !version.startsWith("2.5.0-");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new ServerInstrumentation(), new RouteInstrumentation());
  }
}
