package datadog.opentracing.decorators;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import datadog.opentracing.DDSpanContext;
import datadog.trace.api.DDTags;
import datadog.trace.api.util.ConfigUtils;
import io.opentracing.tag.Tags;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Decorator for servlet contrib */
public class URLAsResourceName extends AbstractDecorator {
  public static final String CONFIG_PATH = "dd-trace";

  public static final Config.Rule RULE_QPARAM = new Config.Rule("\\?.*$", "");
  public static final Config.Rule RULE_DIGIT = new Config.Rule("\\d+", "?");
  private List<Config.Rule> patterns = new ArrayList<>();

  public URLAsResourceName() {
    this(CONFIG_PATH);
  }

  public URLAsResourceName(final String configPath) {

    super();
    this.setMatchingTag(Tags.HTTP_URL.getKey());
    this.setSetTag(DDTags.RESOURCE_NAME);

    try {
      final Config config = ConfigUtils.loadConfigFromResource(configPath, Config.class);
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
    try {
      final String statusCode = String.valueOf(context.getTags().get(Tags.HTTP_STATUS.getKey()));
      // do nothing if the status code is already set and equals to 404.
      // TODO: it assumes that Status404Decorator is active. If it's not, it will lead to unexpected behaviors
      if (statusCode != null && statusCode.equals("404")) {
        return true;
      }

      // Get the path without host:port
      String path = String.valueOf(value);

      try {
        path = new java.net.URL(path).getPath();
      } catch (final MalformedURLException e) {
        // do nothing, use the value instead of the path
      }
      // normalize the path
      path = norm(path);

      // if the verb (GET, POST ...) is present, add it
      final String verb = (String) context.getTags().get(Tags.HTTP_METHOD.getKey());
      if (verb != null && !verb.isEmpty()) {
        path = verb + " " + path;
      }

      context.setResourceName(path);
    } catch (final Throwable e) {
      return false;
    }
    return true;
  }

  // Method to normalise the url string
  String norm(final String origin) {

    String norm = origin;

    // Apply rules
    for (final Config.Rule p : patterns) {
      norm = norm.replaceAll(p.regex, p.replacement);
      // if the rule is final, so do not apply others functions
      if (p.isFinal) {
        break;
      }
    }

    return norm;
  }

  // For tests
  List<Config.Rule> getPatterns() {
    return patterns;
  }

  // For tests
  void setPatterns(final List<Config.Rule> patterns) {
    this.patterns = patterns;
  }

  /** Properties concerning the UrlAsResourceDecorator in the YAML config */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Config {

    List<Rule> urlResourcePatterns;

    public List<Rule> getUrlResourcePatterns() {
      return urlResourcePatterns;
    }

    public void setUrlResourcePatterns(final List<Rule> urlResourcePatterns) {
      this.urlResourcePatterns = urlResourcePatterns;
    }

    public static class Rule {

      String regex;
      String replacement;
      boolean isFinal = false;

      public Rule() {}

      public Rule(final String regex, final String replacement) {
        this.regex = regex;
        this.replacement = replacement;
      }

      public String getRegex() {
        return regex;
      }

      public void setRegex(final String regex) {
        this.regex = regex;
      }

      public String getReplacement() {
        return replacement;
      }

      public void setReplacement(final String replacement) {
        this.replacement = replacement;
      }

      public boolean isFinal() {
        return isFinal;
      }

      public void setFinal(final boolean isFinal) {
        this.isFinal = isFinal;
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
        return Objects.hash(regex, replacement, isFinal);
      }
    }
  }
}
