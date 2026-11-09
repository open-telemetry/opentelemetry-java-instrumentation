/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.internal;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import javax.annotation.Nullable;

/**
 * Shared initialization-time text-pattern enhancement.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class LogPatternInjection {
  private static final boolean ENABLED =
      DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "logging")
          .get("pattern_injection")
          .getBoolean("enabled", false);

  public static boolean isEnabled() {
    return ENABLED;
  }

  @Nullable
  public static String inject(@Nullable String pattern, Framework framework) {
    if (!ENABLED) {
      return pattern;
    }
    return enhance(
        pattern,
        framework,
        AgentCommonConfig.get().getTraceIdKey(),
        AgentCommonConfig.get().getSpanIdKey());
  }

  @Nullable
  static String enhance(
      @Nullable String pattern, Framework framework, String traceKey, String spanKey) {
    if (pattern == null || pattern.isEmpty()) {
      return pattern;
    }
    StringBuilder fields = new StringBuilder();
    for (String key : new String[] {"service.name", traceKey, spanKey}) {
      if (!contains(pattern, framework, key)) {
        if (fields.length() > 0) {
          fields.append(' ');
        }
        fields.append(key).append("=%X{").append(key).append('}');
      }
    }
    if (fields.length() == 0) {
      return pattern;
    }
    int end = 0;
    while (end < pattern.length() && Character.isWhitespace(pattern.charAt(end))) {
      end++;
    }
    if (end == pattern.length()) {
      return pattern;
    }
    boolean converterSeen = false;
    while (end < pattern.length()) {
      char c = pattern.charAt(end);
      if (Character.isWhitespace(c)) {
        break;
      }
      if (framework == Framework.LOGBACK && c == '\\') {
        end = Math.min(pattern.length(), end + 2);
        continue;
      }
      if (c == '%') {
        if (converterSeen) {
          break;
        }
        end = converterEnd(pattern, end, framework);
        converterSeen = true;
      } else {
        end++;
      }
    }
    return pattern.substring(0, end)
        + " "
        + fields
        + (end < pattern.length() && !Character.isWhitespace(pattern.charAt(end)) ? " " : "")
        + pattern.substring(end);
  }

  private static boolean contains(String pattern, Framework framework, String key) {
    for (int i = 0; i < pattern.length(); i++) {
      if (framework == Framework.LOGBACK && pattern.charAt(i) == '\\') {
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
              || (framework == Framework.LOGBACK && converter.equals("mdc"))
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
          if (framework == Framework.LOGBACK) {
            option = firstLogbackOption(option);
          }
          if (option.equals(key)
              || (framework == Framework.LOGBACK && option.startsWith(key + ":-"))
              || ((framework == Framework.LOGBACK || framework == Framework.LOG4J2)
                  && option.isEmpty())) {
            return true;
          }
          if (framework == Framework.LOG4J2) {
            for (String item : option.split(",")) {
              if (item.trim().equals(key)) {
                return true;
              }
            }
          }
        }
      }
      // Descend into presentation wrappers, but never interpret option text as converters.
      if (framework == Framework.LOGBACK && end < pattern.length() && pattern.charAt(end) == '(') {
        int close = balancedEnd(pattern, end, '(', ')', framework);
        if (close > end + 1 && contains(pattern.substring(end + 1, close - 1), framework, key)) {
          return true;
        }
        end = close;
      }
      i = end - 1;
      if (end < pattern.length() && pattern.charAt(end) == '{') {
        int close = balancedEnd(pattern, end, '{', '}', framework);
        if (framework == Framework.LOG4J2
            && (converter.equals("highlight")
                || converter.equals("style")
                || converter.equals("replace")
                || converter.equals("enc")
                || converter.equals("encode")
                || converter.equals("notEmpty")
                || converter.equals("maxLen"))) {
          if (contains(pattern.substring(end + 1, close - 1), framework, key)) {
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
    if (framework != Framework.LOG4J1
        && framework != Framework.LOG4J1_WITH_MDC_MAP
        && end < pattern.length()
        && pattern.charAt(end) == '(') {
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
    if (framework == Framework.LOGBACK && open == '{') {
      return logbackOptionEnd(pattern, start);
    }
    if ((framework == Framework.LOG4J1 || framework == Framework.LOG4J1_WITH_MDC_MAP)
        && open == '{') {
      int end = pattern.indexOf('}', start + 1);
      return end < 0 ? pattern.length() : end + 1;
    }
    int depth = 0;
    for (int i = start; i < pattern.length(); i++) {
      char c = pattern.charAt(i);
      if (framework == Framework.LOGBACK && c == '\\') {
        i++;
      } else if (framework == Framework.LOGBACK && open == '(' && c == '{') {
        i = logbackOptionEnd(pattern, i) - 1;
      } else if (c == open) {
        depth++;
      } else if (c == close && --depth == 0) {
        return i + 1;
      }
    }
    return pattern.length();
  }

  private LogPatternInjection() {}

  /**
   * This class is internal and is hence not for public use. Its APIs are unstable and can change at
   * any time.
   */
  public enum Framework {
    LOGBACK,
    LOG4J1,
    LOG4J2,
    LOG4J1_WITH_MDC_MAP
  }
}
