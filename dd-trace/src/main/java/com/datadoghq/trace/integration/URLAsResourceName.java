package com.datadoghq.trace.integration;

import com.datadoghq.trace.DDSpanContext;
import com.datadoghq.trace.DDTags;
import com.datadoghq.trace.resolver.DDDecoratorsFactory;
import com.datadoghq.trace.resolver.FactoryUtils;
import com.datadoghq.trace.resolver.TracerConfig;
import io.opentracing.tag.Tags;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;

/** Decorator for servlet contrib */
public class URLAsResourceName extends AbstractDecorator {

  private List<Config.Rule> patterns = new ArrayList<>();
  private boolean isConfigured = false;

  public URLAsResourceName() {
    this(DDDecoratorsFactory.CONFIG_PATH);
  }

  public URLAsResourceName(final String configPath) {

    super();
    this.setMatchingTag(Tags.HTTP_URL.getKey());
    this.setSetTag(DDTags.RESOURCE_NAME);

    try {
      final Config config = FactoryUtils.loadConfigFromResource(configPath, Config.class);
      for (final Config.Rule pattern : config.urlResourcePatterns) {
        patterns.add(pattern);
      }

      isConfigured = true;
    } catch (final Throwable ex) {
      isConfigured = false;
    }
  }

  @Override
  public boolean afterSetTag(final DDSpanContext context, final String tag, final Object value) {
    //Assign resource name
    if (context.getTags().containsKey(Tags.COMPONENT.getKey())
        && "java-web-servlet".equals(context.getTags().get(Tags.COMPONENT.getKey()))) {
      try {
        final String path = new java.net.URL(String.valueOf(value)).getPath();
        context.setResourceName(path);
      } catch (final MalformedURLException e) {
        context.setResourceName(String.valueOf(value));
      }
    }
    return true;
  }

  // Method to normalise the url string
  String norm(final String origin) {

    // Remove query params and replace integers
    String norm = origin.replaceAll("\\?.*$", "");
    norm = norm.replaceAll("\\d+", "<not-alpha>");

    // Apply custom rules
    if (isConfigured) {
      for (final Config.Rule p : patterns) {
        norm = norm.replaceAll(p.regex, p.replacement);
      }
    }

    return norm;
  }

  // for tests
  List<Config.Rule> getPatterns() {
    return patterns;
  }

  void setPatterns(final List<Config.Rule> patterns) {
    isConfigured = true;
    this.patterns = patterns;
  }

  public static class Config extends TracerConfig {

    public List<Rule> urlResourcePatterns;

    public static class Rule {

      public String regex;
      public String replacement;

      public Rule() {}

      public Rule(String regex, String replacement) {
        this.regex = regex;
        this.replacement = replacement;
      }
    }
  }
}
