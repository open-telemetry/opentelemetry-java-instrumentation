/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.resources;

import static java.util.logging.Level.FINE;
import static java.util.logging.Level.FINER;

import com.google.auto.service.AutoService;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import io.opentelemetry.sdk.autoconfigure.spi.internal.ConditionalResourceProvider;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.ServiceAttributes;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

/**
 * A ResourceProvider that will attempt to guess the application name for a Spring Boot service.
 * When successful, it will return a Resource that has the service name attribute populated with the
 * name of the Spring Boot application. It uses the following strategies, and the first successful
 * strategy wins:
 *
 * <ul>
 *   <li>Check for the SPRING_APPLICATION_NAME environment variable
 *   <li>Check for spring.application.name system property
 *   <li>Check for application.properties file on the classpath
 *   <li>Check for application.properties in the current working dir
 *   <li>Check for application.yml on the classpath
 *   <li>Check for application.yml in the current working dir
 *   <li>Check for --spring.application.name program argument (not jvm arg) via ProcessHandle
 *   <li>Check for --spring.application.name program argument via sun.java.command system property
 * </ul>
 *
 * <p>Note: The spring starter already includes provider in
 * io.opentelemetry.instrumentation.spring.autoconfigure.resources.SpringResourceProvider
 */
@AutoService(ResourceProvider.class)
public class SpringBootServiceNameDetector implements ConditionalResourceProvider {

  private static final Logger logger =
      Logger.getLogger(SpringBootServiceNameDetector.class.getName());
  private static final String COMMANDLINE_ARG_PREFIX = "--spring.application.name=";
  private static final Pattern COMMANDLINE_PATTERN =
      Pattern.compile("--spring\\.application\\.name=([a-zA-Z.\\-_]+)");
  private final SystemHelper system;

  @SuppressWarnings("unused")
  public SpringBootServiceNameDetector() {
    this(new SystemHelper());
  }

  // Exists for testing
  SpringBootServiceNameDetector(SystemHelper system) {
    this.system = system;
  }

  @Override
  public Resource createResource(ConfigProperties config) {

    logger.log(FINER, "Performing Spring Boot service name auto-detection...");
    // Note: The order should be consistent with the order of Spring matching, but noting
    // that we have "first one wins" while Spring has "last one wins".
    // The docs for Spring are here:
    // https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config
    // https://docs.spring.io/spring-cloud-commons/docs/4.0.4/reference/html/#the-bootstrap-application-context
    Stream<Supplier<String>> finders =
        Stream.of(
            this::findByCommandlineArgument,
            this::findBySystemProperties,
            this::findByEnvironmentVariable,
            this::findByCurrentDirectoryApplicationProperties,
            this::findByCurrentDirectoryApplicationYml,
            this::findByCurrentDirectoryApplicationYaml,
            this::findByClasspathApplicationProperties,
            this::findByClasspathApplicationYml,
            this::findByClasspathApplicationYaml,
            this::findByClasspathBootstrapProperties,
            this::findByClasspathBootstrapYml,
            this::findByClasspathBootstrapYaml);
    return finders
        .map(Supplier::get)
        .filter(Objects::nonNull)
        .findFirst()
        .map(
            serviceName -> {
              logger.log(FINE, "Auto-detected Spring Boot service name: {0}", serviceName);
              return Resource.builder().put(ServiceAttributes.SERVICE_NAME, serviceName).build();
            })
        .orElseGet(Resource::empty);
  }

  @Override
  public boolean shouldApply(ConfigProperties config, Resource resource) {
    // we're skipping this provider if the service name was manually set by the user -- no need to
    // waste time trying to compute the service name if it's going to be overridden anyway
    String serviceName = config.getString("otel.service.name");
    Map<String, String> resourceAttributes = config.getMap("otel.resource.attributes");
    return serviceName == null
        && !resourceAttributes.containsKey(ServiceAttributes.SERVICE_NAME.getKey())
        && "unknown_service:java".equals(resource.getAttribute(ServiceAttributes.SERVICE_NAME));
  }

  @Override
  public int order() {
    // make it run later than the default set of providers
    return 100;
  }

  @Nullable
  private String findByEnvironmentVariable() {
    String result = system.getenv("SPRING_APPLICATION_NAME");
    logger.log(FINER, "Checking for SPRING_APPLICATION_NAME in env: {0}", result);
    return result;
  }

  @Nullable
  private String findBySystemProperties() {
    String result = system.getProperty("spring.application.name");
    logger.log(FINER, "Checking for spring.application.name system property: {0}", result);
    return result;
  }

  @Nullable
  private String findByClasspathApplicationProperties() {
    return findByClasspathPropertiesFile("application.properties");
  }

  @Nullable
  private String findByClasspathBootstrapProperties() {
    return findByClasspathPropertiesFile("bootstrap.properties");
  }

  @Nullable
  private String findByCurrentDirectoryApplicationProperties() {
    String result = null;
    try (InputStream in = system.openFile("application.properties")) {
      result = getAppNamePropertyFromStream(in);
    } catch (Exception e) {
      // expected to fail sometimes
    }
    logger.log(FINER, "Checking application.properties in current dir: {0}", result);
    return result;
  }

  @Nullable
  private String findByClasspathApplicationYml() {
    return findByClasspathYamlFile("application.yml");
  }

  @Nullable
  private String findByClasspathBootstrapYml() {
    return findByClasspathYamlFile("bootstrap.yml");
  }

  @Nullable
  private String findByClasspathApplicationYaml() {
    return findByClasspathYamlFile("application.yaml");
  }

  @Nullable
  private String findByClasspathBootstrapYaml() {
    return findByClasspathYamlFile("bootstrap.yaml");
  }

  private String findByClasspathYamlFile(String fileName) {
    String result = loadFromClasspath(fileName, SpringBootServiceNameDetector::parseNameFromYaml);
    if (logger.isLoggable(FINER)) {
      logger.log(FINER, "Checking {0} in classpath: {1}", new Object[] {fileName, result});
    }
    return result;
  }

  @Nullable
  private String findByCurrentDirectoryApplicationYml() {
    return findByCurrentDirectoryYamlFile("application.yml");
  }

  @Nullable
  private String findByCurrentDirectoryApplicationYaml() {
    return findByCurrentDirectoryYamlFile("application.yaml");
  }

  @Nullable
  private String findByCurrentDirectoryYamlFile(String fileName) {
    String result = null;
    try (InputStream in = system.openFile(fileName)) {
      result = parseNameFromYaml(in);
    } catch (Exception e) {
      // expected to fail sometimes
    }
    if (logger.isLoggable(FINER)) {
      logger.log(FINER, "Checking {0} in current dir: {1}", new Object[] {fileName, result});
    }
    return result;
  }

  @Nullable
  @SuppressWarnings("unchecked")
  private static String parseNameFromYaml(InputStream in) {
    try {
      LoadSettings settings = LoadSettings.builder().build();
      Load yaml = new Load(settings);
      for (Object o : yaml.loadAllFromInputStream(in)) {
        Map<String, Object> data = (Map<String, Object>) o;
        Map<String, Map<String, Object>> spring =
            (Map<String, Map<String, Object>>) data.get("spring");
        if (spring != null) {
          Map<String, Object> app = spring.get("application");
          if (app != null) {
            Object name = app.get("name");
            return (String) name;
          }
        }
      }
    } catch (RuntimeException e) {
      // expected to fail sometimes
    }
    return null;
  }

  @Nullable
  private String findByCommandlineArgument() {
    String result = attemptProcessHandleReflection();
    if (result == null) {
      String javaCommand = system.getProperty("sun.java.command");
      result = parseNameFromCommandLine(javaCommand);
    }
    logger.log(FINER, "Checking application commandline args: {0}", result);
    return result;
  }

  @Nullable
  private String attemptProcessHandleReflection() {
    try {
      String[] args = system.attemptGetCommandLineArgsViaReflection();
      return parseNameFromProcessArgs(args);
    } catch (Exception e) {
      return null;
    }
  }

  @Nullable
  private static String parseNameFromCommandLine(@Nullable String commandLine) {
    if (commandLine == null) {
      return null;
    }
    Matcher matcher = COMMANDLINE_PATTERN.matcher(commandLine);
    if (matcher.find()) { // Required before group()
      return matcher.group(1);
    }
    return null;
  }

  @Nullable
  private static String parseNameFromProcessArgs(String[] args) {
    return Stream.of(args)
        .filter(arg -> arg.startsWith(COMMANDLINE_ARG_PREFIX))
        .map(arg -> arg.substring(COMMANDLINE_ARG_PREFIX.length()))
        .findFirst()
        .orElse(null);
  }

  @Nullable
  private String findByClasspathPropertiesFile(String filename) {
    String result =
        loadFromClasspath(filename, SpringBootServiceNameDetector::getAppNamePropertyFromStream);
    if (logger.isLoggable(FINER)) {
      logger.log(
          FINER,
          "Checking for spring.application.name in {0} file: {1}",
          new Object[] {filename, result});
    }
    return result;
  }

  @Nullable
  private static String getAppNamePropertyFromStream(InputStream in) {
    Properties properties = new Properties();
    try {
      // Note: load() uses ISO 8859-1 encoding, same as spring uses by default for property files
      properties.load(in);
      return properties.getProperty("spring.application.name");
    } catch (IOException e) {
      return null;
    }
  }

  @Nullable
  private String loadFromClasspath(String filename, Function<InputStream, String> parser) {
    try (InputStream in = system.openClasspathResource(filename)) {
      return in != null ? parser.apply(in) : null;
    } catch (Exception e) {
      return null;
    }
  }
}
