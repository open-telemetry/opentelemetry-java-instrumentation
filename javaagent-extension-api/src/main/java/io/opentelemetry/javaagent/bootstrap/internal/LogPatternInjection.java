/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.internal;

import static java.nio.charset.StandardCharsets.UTF_8;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.internal.ScopedThreadValue;
import java.util.Base64;
import javax.annotation.Nullable;

/**
 * Shared initialization-time text-pattern enhancement.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class LogPatternInjection {
  private static final String LOGBACK_TRACE_PREFIX = "otel.pattern-injection.trace.";
  private static final String LOGBACK_SPAN_PREFIX = "otel.pattern-injection.span.";
  private static final String LOG4J2_TRACE_PREFIX = "otel.pattern-injection.log4j2.trace.";
  private static final String LOG4J2_SPAN_PREFIX = "otel.pattern-injection.log4j2.span.";
  private static final String LOG4J1_TRACE_PREFIX = "otel.pattern-injection.log4j1.trace.";
  private static final String LOG4J1_SPAN_PREFIX = "otel.pattern-injection.log4j1.span.";
  private static final Base64.Encoder mdcKeyEncoder = Base64.getUrlEncoder().withoutPadding();
  private static final boolean ENABLED =
      DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "logging")
          .get("pattern_injection")
          .getBoolean("enabled", false);

  private static final ScopedThreadValue<Injection> currentInjection = new ScopedThreadValue<>();

  public static boolean isEnabled() {
    return ENABLED;
  }

  public static ScopedThreadValue<Injection> currentInjection() {
    return currentInjection;
  }

  public static Injection prepare(@Nullable String pattern, Framework framework) {
    return prepare(
        pattern,
        framework,
        AgentCommonConfig.get().getTraceIdKey(),
        AgentCommonConfig.get().getSpanIdKey(),
        ENABLED);
  }

  // visible for testing
  static Injection prepare(
      @Nullable String pattern, Framework framework, String traceKey, String spanKey) {
    return prepare(pattern, framework, traceKey, spanKey, true);
  }

  private static Injection prepare(
      @Nullable String pattern,
      Framework framework,
      String traceKey,
      String spanKey,
      boolean enabled) {
    String traceOption = availableOption(pattern, traceKey, true, framework);
    String spanOption = availableOption(pattern, spanKey, false, framework);
    String enhanced =
        enabled ? enhance(pattern, framework, traceKey, spanKey, traceOption, spanOption) : pattern;
    return new Injection(
        pattern,
        enhanced,
        framework,
        traceKey,
        spanKey,
        traceOption,
        spanOption,
        pattern != null && !pattern.equals(enhanced));
  }

  private static String availableOption(
      @Nullable String pattern, String key, boolean trace, Framework framework) {
    String option = mdcOption(key, trace, framework);
    if (!option.equals(key) && pattern != null) {
      // Existing application converters must never be mistaken for generated aliases.
      while (pattern.contains(option)) {
        option += "_";
      }
    }
    return option;
  }

  @Nullable
  public static String restoreOriginalPattern(@Nullable String pattern, Framework framework) {
    Injection injection = currentInjection.get();
    if (pattern != null
        && injection != null
        && injection.matches(framework)
        && pattern.equals(injection.pattern)) {
      return injection.originalPattern;
    }
    return pattern;
  }

  @Nullable
  public static String resolveMdcKey(@Nullable String key, Framework framework) {
    Injection injection = currentInjection.get();
    if (key == null || injection == null || !injection.matches(framework)) {
      return key;
    }
    if (key.equals(injection.traceOption)) {
      return injection.traceKey;
    }
    if (key.equals(injection.spanOption)) {
      return injection.spanKey;
    }
    return key;
  }

  @Nullable
  static String enhance(
      @Nullable String pattern, Framework framework, String traceKey, String spanKey) {
    return enhance(
        pattern,
        framework,
        traceKey,
        spanKey,
        mdcOption(traceKey, true, framework),
        mdcOption(spanKey, false, framework));
  }

  @Nullable
  private static String enhance(
      @Nullable String pattern,
      Framework framework,
      String traceKey,
      String spanKey,
      String traceOption,
      String spanOption) {
    if (pattern == null || pattern.isEmpty()) {
      return pattern;
    }
    StringBuilder fields = new StringBuilder();
    String[] keys = {traceKey, spanKey};
    for (int i = 0; i < keys.length; i++) {
      String key = keys[i];
      String option = i == 0 ? traceOption : spanOption;
      if (!contains(pattern, framework, key, option)) {
        if (fields.length() > 0) {
          fields.append(' ');
        }
        String label = key;
        if (isLogback(framework)) {
          label = escapeLogbackLiteral(key);
        } else {
          label = key.replace("%", "%%");
        }
        fields.append(label).append("=%X{").append(option).append('}');
      }
    }
    if (fields.length() == 0) {
      return pattern;
    }
    // Log4j2 does not support escaping literal braces inside wrapper options. Keep generated
    // fields outside wrappers when their labels contain braces, preserving native styling.
    boolean nestedInsertion =
        framework != Framework.LOG4J2
            || (traceKey.indexOf('{') < 0
                && traceKey.indexOf('}') < 0
                && spanKey.indexOf('{') < 0
                && spanKey.indexOf('}') < 0);
    int end = messageInsertion(pattern, framework, nestedInsertion);
    if (end < 0) {
      return pattern;
    }
    return pattern.substring(0, end)
        + (end > 0 && !Character.isWhitespace(pattern.charAt(end - 1)) ? " " : "")
        + fields
        + (end < pattern.length() && !Character.isWhitespace(pattern.charAt(end)) ? " " : "")
        + pattern.substring(end);
  }

  private static String mdcOption(String key, boolean trace, Framework framework) {
    boolean simple = !key.isEmpty();
    for (int i = 0; i < key.length(); i++) {
      char c = key.charAt(i);
      if (!Character.isLetterOrDigit(c) && c != '_' && c != '.' && c != '-') {
        simple = false;
        break;
      }
    }
    if (simple) {
      return key;
    }
    // Native option parsers cannot represent every MDC key. Restore generated options
    // in the native converter after it has parsed them, without adding entries to the MDC map.
    String prefix;
    switch (framework) {
      case LOGBACK:
      case LOGBACK_LEGACY:
        prefix = trace ? LOGBACK_TRACE_PREFIX : LOGBACK_SPAN_PREFIX;
        break;
      case LOG4J2:
        prefix = trace ? LOG4J2_TRACE_PREFIX : LOG4J2_SPAN_PREFIX;
        break;
      default:
        prefix = trace ? LOG4J1_TRACE_PREFIX : LOG4J1_SPAN_PREFIX;
        break;
    }
    return prefix + mdcKeyEncoder.encodeToString(key.getBytes(UTF_8));
  }

  private static String escapeLogbackLiteral(String value) {
    return value.replace("\\", "\\\\").replace("%", "\\%").replace("(", "\\(").replace(")", "\\)");
  }

  private static boolean contains(
      String pattern, Framework framework, String key, String mdcOption) {
    for (int i = 0; i < pattern.length(); i++) {
      if (isLogback(framework) && pattern.charAt(i) == '\\') {
        i++;
        continue;
      }
      if (pattern.charAt(i) != '%') {
        continue;
      }
      if (i + 1 < pattern.length() && pattern.charAt(i + 1) == '%') {
        i++;
        continue;
      }
      int name = i + 1;
      while (name < pattern.length() && "-0123456789.".indexOf(pattern.charAt(name)) >= 0) {
        name++;
      }
      int end = name;
      while (end < pattern.length() && Character.isLetter(pattern.charAt(end))) {
        end++;
        if (framework == Framework.LOG4J1 || framework == Framework.LOG4J1_WITH_MDC_MAP) {
          break;
        }
      }
      String converter = pattern.substring(name, end);
      boolean mdc =
          converter.equals("X")
              || (isLogback(framework) && converter.equals("mdc"))
              || (framework == Framework.LOG4J2
                  && (converter.equals("mdc") || converter.equals("MDC")));
      if (mdc) {
        if (end == pattern.length() || pattern.charAt(end) != '{') {
          if (framework != Framework.LOG4J1) {
            return true;
          }
        } else {
          int close = balancedEnd(pattern, end, '{', '}', framework);
          String option = pattern.substring(end + 1, Math.max(end + 1, close - 1));
          if (option.equals(mdcOption)) {
            return true;
          }
          if (isLogback(framework)) {
            option = firstLogbackOption(option);
            if (option.isEmpty()) {
              return true;
            }
            if (framework == Framework.LOGBACK) {
              int delimiter = option.indexOf(":-");
              if (delimiter >= 0) {
                option = option.substring(0, delimiter);
              }
            }
          }
          if (option.equals(mdcOption)) {
            return true;
          }
          if (option.equals(key) && (framework != Framework.LOG4J2 || option.indexOf(',') <= 0)) {
            return true;
          }
          if (framework == Framework.LOG4J2 && option.indexOf(',') > 0) {
            for (String item : option.split(",")) {
              if (item.trim().equals(key)) {
                return true;
              }
            }
          }
        }
      }
      // Descend into presentation wrappers, but never interpret option text as converters.
      if (isLogback(framework) && end < pattern.length() && pattern.charAt(end) == '(') {
        int close = balancedEnd(pattern, end, '(', ')', framework);
        if (close > end + 1
            && contains(pattern.substring(end + 1, close - 1), framework, key, mdcOption)) {
          return true;
        }
        end = close;
      }
      i = end - 1;
      if (end < pattern.length() && pattern.charAt(end) == '{') {
        int close = balancedEnd(pattern, end, '{', '}', framework);
        if (framework == Framework.LOG4J2 && isLog4j2Wrapper(converter)) {
          if (contains(pattern.substring(end + 1, close - 1), framework, key, mdcOption)) {
            return true;
          }
        }
        i = close - 1;
        while (i + 1 < pattern.length() && pattern.charAt(i + 1) == '{') {
          i = balancedEnd(pattern, i + 1, '{', '}', framework) - 1;
        }
      }
    }
    return false;
  }

  private static int converterEnd(String pattern, int start, Framework framework) {
    int end = start + 1;
    if (end < pattern.length() && pattern.charAt(end) == '%') {
      return end + 1;
    }
    while (end < pattern.length() && "-0123456789.".indexOf(pattern.charAt(end)) >= 0) {
      end++;
    }
    while (end < pattern.length() && Character.isLetter(pattern.charAt(end))) {
      end++;
      if (framework == Framework.LOG4J1 || framework == Framework.LOG4J1_WITH_MDC_MAP) {
        break;
      }
    }
    if (isLogback(framework) && end < pattern.length() && pattern.charAt(end) == '(') {
      end = balancedEnd(pattern, end, '(', ')', framework);
    }
    while (end < pattern.length() && pattern.charAt(end) == '{') {
      end = balancedEnd(pattern, end, '{', '}', framework);
    }
    return end;
  }

  private static String firstLogbackOption(String options) {
    options = options.trim();
    if (options.isEmpty()) {
      return options;
    }
    char quote = options.charAt(0);
    if (quote == '\'' || quote == '"') {
      for (int i = 1; i < options.length(); i++) {
        if (options.charAt(i) == '\\') {
          i++;
        } else if (options.charAt(i) == quote) {
          return options.substring(1, i);
        }
      }
      return options;
    }
    int comma = options.indexOf(',');
    return (comma < 0 ? options : options.substring(0, comma)).trim();
  }

  private static int logbackOptionEnd(String pattern, int start) {
    // Logback options are comma-separated tokens, not nested brace groups. Only a quote
    // at the beginning of an option starts quoted text (OptionTokenizer semantics).
    boolean optionStart = true;
    char quote = 0;
    for (int i = start + 1; i < pattern.length(); i++) {
      char c = pattern.charAt(i);
      if (quote != 0) {
        if (c == '\\') {
          i++;
        } else if (c == quote) {
          quote = 0;
        }
      } else if (c == '}') {
        return i + 1;
      } else if (c == ',') {
        optionStart = true;
      } else if (optionStart && !Character.isWhitespace(c)) {
        optionStart = false;
        if (c == '\'' || c == '"') {
          quote = c;
        }
      }
    }
    return pattern.length();
  }

  private static int balancedEnd(
      String pattern, int start, char open, char close, Framework framework) {
    if (isLogback(framework) && open == '{') {
      return logbackOptionEnd(pattern, start);
    }
    if ((framework == Framework.LOG4J1 || framework == Framework.LOG4J1_WITH_MDC_MAP)
        && open == '{') {
      int end = pattern.indexOf('}', start + 1);
      return end < 0 ? pattern.length() : end + 1;
    }
    if (isLogback(framework) && open == '(') {
      for (int i = start + 1; i < pattern.length(); i++) {
        char c = pattern.charAt(i);
        if (c == '\\') {
          i++;
        } else if (c == '%') {
          i = converterEnd(pattern, i, framework) - 1;
        } else if (c == ')') {
          return i + 1;
        }
      }
      return pattern.length();
    }
    int depth = 0;
    for (int i = start; i < pattern.length(); i++) {
      char c = pattern.charAt(i);
      if (isLogback(framework) && c == '\\') {
        i++;
      } else if (c == open) {
        depth++;
      } else if (c == close && --depth == 0) {
        return i + 1;
      }
    }
    return pattern.length();
  }

  private static boolean isLogback(Framework framework) {
    return framework == Framework.LOGBACK || framework == Framework.LOGBACK_LEGACY;
  }

  private static boolean isLog4j2Wrapper(String converter) {
    return converter.equals("highlight")
        || converter.equals("style")
        || converter.equals("replace")
        || converter.equals("enc")
        || converter.equals("encode")
        || converter.equals("notEmpty")
        || converter.equals("varsNotEmpty")
        || converter.equals("variablesNotEmpty")
        || converter.equals("maxLen")
        || converter.equals("maxLength")
        || converter.equals("black")
        || converter.equals("blue")
        || converter.equals("cyan")
        || converter.equals("green")
        || converter.equals("magenta")
        || converter.equals("red")
        || converter.equals("white")
        || converter.equals("yellow");
  }

  // Returns an insertion offset, -1 for literal-only text, or -2 for prefix fields without a
  // message.
  private static int messageInsertion(
      String pattern, Framework framework, boolean nestedInsertion) {
    boolean prefixSeen = false;
    int precedingEnd = 0;
    while (precedingEnd < pattern.length()
        && Character.isWhitespace(pattern.charAt(precedingEnd))) {
      precedingEnd++;
    }
    for (int i = 0; i < pattern.length(); i++) {
      if (isLogback(framework) && pattern.charAt(i) == '\\') {
        i++;
        continue;
      }
      if (pattern.charAt(i) != '%') {
        continue;
      }
      if (i + 1 < pattern.length() && pattern.charAt(i + 1) == '%') {
        i++;
        continue;
      }
      int name = i + 1;
      while (name < pattern.length() && "-0123456789.".indexOf(pattern.charAt(name)) >= 0) {
        name++;
      }
      int end = name;
      while (end < pattern.length() && Character.isLetter(pattern.charAt(end))) {
        end++;
        if (framework == Framework.LOG4J1 || framework == Framework.LOG4J1_WITH_MDC_MAP) {
          break;
        }
      }
      String converter = pattern.substring(name, end);
      if (converter.equals("m")
          || ((isLogback(framework) || framework == Framework.LOG4J2)
              && (converter.equals("msg") || converter.equals("message")))) {
        return precedingEnd;
      }
      char open = isLogback(framework) ? '(' : '{';
      char close = isLogback(framework) ? ')' : '}';
      if (end < pattern.length()
          && pattern.charAt(end) == open
          && (isLogback(framework)
              || (framework == Framework.LOG4J2 && isLog4j2Wrapper(converter)))) {
        int wrapperEnd = balancedEnd(pattern, end, open, close, framework);
        int nested =
            messageInsertion(
                pattern.substring(end + 1, wrapperEnd - 1), framework, nestedInsertion);
        if (nested >= 0) {
          if (!nestedInsertion) {
            return precedingEnd;
          }
          return nested == 0 ? precedingEnd : end + 1 + nested;
        }
        if (nested == -1) {
          // A wrapper containing only literal text belongs to the message prefix.
          i = converterEnd(pattern, i, framework) - 1;
          continue;
        }
      }
      i = converterEnd(pattern, i, framework) - 1;
      prefixSeen = true;
      precedingEnd = i + 1;
      // Keep a closing bracket or other adjacent literal suffix with the preceding field.
      while (precedingEnd < pattern.length()
          && !Character.isWhitespace(pattern.charAt(precedingEnd))
          && pattern.charAt(precedingEnd) != '%') {
        precedingEnd++;
      }
      while (precedingEnd < pattern.length()
          && Character.isWhitespace(pattern.charAt(precedingEnd))) {
        precedingEnd++;
      }
    }
    return prefixSeen ? -2 : -1;
  }

  /**
   * Initialization state scoped to one native pattern compilation, never a log event.
   *
   * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
   * at any time.
   */
  public static final class Injection {
    @Nullable private final String originalPattern;
    @Nullable private final String pattern;
    private final Framework framework;
    private final String traceKey;
    private final String spanKey;
    private final String traceOption;
    private final String spanOption;
    private final boolean modified;

    private Injection(
        @Nullable String originalPattern,
        @Nullable String pattern,
        Framework framework,
        String traceKey,
        String spanKey,
        String traceOption,
        String spanOption,
        boolean modified) {
      this.originalPattern = originalPattern;
      this.pattern = pattern;
      this.framework = framework;
      this.traceKey = traceKey;
      this.spanKey = spanKey;
      this.traceOption = traceOption;
      this.spanOption = spanOption;
      this.modified = modified;
    }

    @Nullable
    public String getPattern() {
      return pattern;
    }

    private boolean matches(Framework other) {
      return modified
          && (framework == other
              || (isLogback(framework) && isLogback(other))
              || (framework == Framework.LOG4J1_WITH_MDC_MAP && other == Framework.LOG4J1));
    }
  }

  private LogPatternInjection() {}

  /**
   * This class is internal and is hence not for public use. Its APIs are unstable and can change at
   * any time.
   */
  public enum Framework {
    LOGBACK,
    LOGBACK_LEGACY,
    LOG4J1,
    LOG4J2,
    LOG4J1_WITH_MDC_MAP
  }
}
