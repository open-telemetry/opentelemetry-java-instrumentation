package io.opentelemetry.agents;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

public class Agent {

  final static String OTEL_LATEST = "https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent-all.jar";

  public final static Agent NONE = new Agent("none", "no agent at all");
  public final static Agent LATEST_RELEASE = new Agent("latest", "latest mainstream release", OTEL_LATEST);
  public final static Agent LATEST_SNAPSHOT = new Agent("snapshot", "latest available snapshot version from main");

  private final String name;
  private final String description;
  private final URL url;

  public Agent(String name, String description) {
    this(name, description, null);
  }

  public Agent(String name, String description, String url) {
    this.name = name;
    this.description = description;
    this.url = makeUrl(url);
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public boolean hasUrl(){
    return url != null;
  }

  public URL getUrl() {
    return url;
  }

  private static URL makeUrl(String url) {
    try {
      if(url == null) return null;
      return URI.create(url).toURL();
    } catch (MalformedURLException e) {
      throw new RuntimeException("Error parsing url", e);
    }
  }
}
