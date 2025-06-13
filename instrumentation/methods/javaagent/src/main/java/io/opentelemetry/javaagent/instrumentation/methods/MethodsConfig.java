package io.opentelemetry.javaagent.instrumentation.methods;

import static java.util.Collections.emptyList;

import com.google.common.base.Strings;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MethodsConfig {

  private static final Logger logger = Logger.getLogger(MethodsConfig.class.getName());

  private MethodsConfig() {}

  static List<TypeInstrumentation> parseDeclarativeConfig(DeclarativeConfigProperties methods) {
    return methods.getStructuredList("include", emptyList()).stream()
        .flatMap(MethodsConfig::parseMethodInstrumentation)
        .collect(Collectors.toList());
  }

  private static Stream<MethodInstrumentation> parseMethodInstrumentation(
      DeclarativeConfigProperties config) {
    String clazz = config.getString("class");
    if (Strings.isNullOrEmpty(clazz)) {
      logger.log(Level.WARNING, "Invalid methods configuration - class name missing: {0}", config);
      return Stream.empty();
    }
    Set<String> internal = new HashSet<>();
    Set<String> server = new HashSet<>();
    Set<String> client = new HashSet<>();

    List<DeclarativeConfigProperties> methods = config.getStructuredList("methods", emptyList());
    for (DeclarativeConfigProperties method : methods) {
      String methodName = method.getString("name");
      if (Strings.isNullOrEmpty(methodName)) {
        logger.log(
            Level.WARNING, "Invalid methods configuration - method name missing: {0}", method);
        continue;
      }
      String spanKind = method.getString("span_kind", "internal");
      if ("internal".equalsIgnoreCase(spanKind)) {
        internal.add(methodName);
      } else if ("server".equalsIgnoreCase(spanKind)) {
        server.add(methodName);
      } else if ("client".equalsIgnoreCase(spanKind)) {
        client.add(methodName);
      } else {
        logger.log(Level.WARNING, "Invalid methods configuration - unknown span_kind: {0}", method);
      }
    }

    if (internal.isEmpty() && server.isEmpty() && client.isEmpty()) {
      logger.log(Level.WARNING, "Invalid methods configuration - no methods defined: {0}", config);
      return Stream.empty();
    }

    return Stream.of(new MethodInstrumentation(clazz, internal, server, client));
  }
}
