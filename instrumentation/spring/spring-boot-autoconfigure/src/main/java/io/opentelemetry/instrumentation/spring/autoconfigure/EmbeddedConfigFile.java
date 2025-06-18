package io.opentelemetry.instrumentation.spring.autoconfigure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.incubator.config.GlobalConfigProvider;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.extension.incubator.fileconfig.DeclarativeConfiguration;
import io.opentelemetry.sdk.extension.incubator.fileconfig.SdkConfigProvider;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.OpenTelemetryConfigurationModel;
import org.springframework.boot.env.OriginTrackedMapPropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class EmbeddedConfigFile {

  private static final Pattern PATTERN = Pattern.compile(
            "^Config resource 'class path resource \\[(.+)]' via location 'optional:classpath:/'$"
    );

  static OpenTelemetryConfigurationModel extractModel(ConfigurableEnvironment environment)
      throws IOException {
    for (PropertySource<?> propertySource : environment.getPropertySources()) {
      if (propertySource instanceof OriginTrackedMapPropertySource) {
        OriginTrackedMapPropertySource source = (OriginTrackedMapPropertySource) propertySource;
        String name = source.getName();
        System.out.println("Property Source: " + name); // todo remove
        Matcher matcher = PATTERN.matcher(name);
        if (matcher.matches()) {
          String file = matcher.group(1);
          System.out.println("Found application.yaml: " + file);

          try (InputStream resourceAsStream =
              environment.getClass().getClassLoader().getResourceAsStream(file)) {
            // Print the contents of the application.yaml file
            if (resourceAsStream != null) {
              String content = new String(resourceAsStream.readAllBytes());
              System.out.println("Contents of " + file + ":");  // todo remove
              System.out.println(content);             // todo remove

              extractOtelConfigFile(content);
            } else {
              System.out.println("Could not find the application.yaml file in the classpath.");  // todo remove
            }
          }
        }
      }
    }
  }

  private static void extractOtelConfigFile(String content) throws IOException {
    //
    //	https://github.com/open-telemetry/opentelemetry-configuration/blob/c205770a956713e512eddb056570a99737e3383a/examples/kitchen-sink.yaml#L11

    // 1. read to yaml tree in jackson
    ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    JsonNode rootNode = yamlMapper.readTree(content);

    // 2. find the "otel" node
    JsonNode otelNode = rootNode.get("otel");
    if (otelNode == null) {
      System.out.println("No 'otel' configuration found in the YAML file.");  // todo remove
      return;
    }

    String str = yamlMapper.writeValueAsString(otelNode);

    OpenTelemetryConfigurationModel model =
        DeclarativeConfiguration.parse(
            new ByteArrayInputStream(str.getBytes(StandardCharsets.UTF_8)));
    OpenTelemetrySdk sdk =
        DeclarativeConfiguration.create(model); // can pass ComponentLoader as second arg

    Runtime.getRuntime().addShutdownHook(new Thread(sdk::close));

    GlobalOpenTelemetry.set(sdk);
    GlobalConfigProvider.set(SdkConfigProvider.create(model));

    System.out.println("OpenTelemetry SDK initialized with configuration from: " + sdk);  // todo remove
    System.out.println("OpenTelemetry configuration file content:");                                     // todo remove
    System.out.println(str);                                                                                            // todo remove
  }
}
