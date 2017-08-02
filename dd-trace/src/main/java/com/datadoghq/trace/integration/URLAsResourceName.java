package com.datadoghq.trace.integration;

import com.datadoghq.trace.DDSpanContext;
import com.datadoghq.trace.DDTags;
import com.datadoghq.trace.resolver.DDTracerFactory;
import com.datadoghq.trace.resolver.FactoryUtils;
import com.datadoghq.trace.resolver.TracerConfig;
import io.opentracing.tag.Tags;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Decorator for servlet contrib */
public class URLAsResourceName extends AbstractDecorator {

  public static final Config.Rule RULE_QPARAM = new Config.Rule("\\?.*$", "");
  public static final Config.Rule RULE_DIGIT = new Config.Rule("\\d+", "?");
  private List<Config.Rule> patterns = new ArrayList<>();

  public URLAsResourceName() {
    this(DDTracerFactory.CONFIG_PATH);
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
    } catch (final Throwable ex) {
      // do nothing
    } finally {
      patterns.add(RULE_QPARAM);
      patterns.add(RULE_DIGIT);
    }
  }

  @Override
  public boolean afterSetTag(final DDSpanContext context, final String tag, final Object value) {
    //Assign resource name
    try {
      String path = String.valueOf(value);
      try {
        path = new java.net.URL(path).getPath();
      } catch (final MalformedURLException e) {
        // do nothing
      }
      path = norm(path);
      final String verb = (String) context.getTags().get(Tags.HTTP_METHOD.getKey());
      if (verb != null && !verb.isEmpty()) {
        path = verb + " " + path;
      }
      context.setResourceName(path);
    } catch (final Throwable e) {
      // do nothing
    }
    return true;
  }

  // Method to normalise the url string
  String norm(final String origin) {

    // Remove query params and replace integers
    String norm = origin;

    // Apply custom rules
    for (final Config.Rule p : patterns) {
      norm = norm.replaceAll(p.regex, p.replacement);
    }

    return norm;
  }

  // for tests
  List<Config.Rule> getPatterns() {
    return patterns;
  }

  void setPatterns(final List<Config.Rule> patterns) {
    this.patterns = patterns;
  }

  public static class Config extends TracerConfig {

    public List<Rule> urlResourcePatterns;

    public static class Rule {

      public String regex;
      public String replacement;

      public Rule() {}

      public Rule(final String regex, final String replacement) {
        this.regex = regex;
        this.replacement = replacement;
      }

      @Override
      public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final Rule rule = (Rule) o;
        return Objects.equals(regex, rule.regex) && Objects.equals(replacement, rule.replacement);
      }

      @Override
      public int hashCode() {
        return Objects.hash(regex, replacement);
      }
    }
  }
}
