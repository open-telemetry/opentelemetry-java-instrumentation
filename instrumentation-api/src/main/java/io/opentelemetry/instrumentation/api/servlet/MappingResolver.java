/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.servlet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Helper class for finding a mapping that matches current request from a collection of mappings.
 */
public final class MappingResolver {
  private final Set<String> exactMatches;
  private final List<WildcardMatcher> wildcardMatchers;
  private final boolean hasDefault;

  private MappingResolver(
      Set<String> exactMatches, List<WildcardMatcher> wildcardMatchers, boolean hasDefault) {
    this.exactMatches = exactMatches.isEmpty() ? Collections.emptySet() : exactMatches;
    this.wildcardMatchers = wildcardMatchers.isEmpty() ? Collections.emptyList() : wildcardMatchers;
    this.hasDefault = hasDefault;
  }

  public static MappingResolver build(Collection<String> mappings) {
    List<WildcardMatcher> wildcardMatchers = new ArrayList<>();
    Set<String> exactMatches = new HashSet<>();
    boolean hasDefault = false;
    for (String mapping : mappings) {
      if (mapping.equals("")) {
        exactMatches.add("/");
      } else if (mapping.equals("/") || mapping.equals("/*")) {
        hasDefault = true;
      } else if (mapping.startsWith("*.") && mapping.length() > 2) {
        wildcardMatchers.add(new SuffixMatcher("/" + mapping, mapping.substring(1)));
      } else if (mapping.endsWith("/*")) {
        wildcardMatchers.add(
            new PrefixMatcher(mapping, mapping.substring(0, mapping.length() - 2)));
      } else {
        exactMatches.add(mapping);
      }
    }

    // wildfly has empty mappings for default servlet
    if (mappings.isEmpty()) {
      hasDefault = true;
    }

    return new MappingResolver(exactMatches, wildcardMatchers, hasDefault);
  }

  /** Find mapping for requested path. */
  @Nullable
  public String resolve(@Nullable String servletPath, @Nullable String pathInfo) {
    if (servletPath == null) {
      return null;
    }

    // get full path inside context
    String path = servletPath;
    if (pathInfo != null) {
      path += pathInfo;
    }
    // trim trailing /
    if (path.endsWith("/") && !path.equals("/")) {
      path = path.substring(0, path.length() - 1);
    }

    if (exactMatches.contains(path)) {
      return path;
    }

    for (WildcardMatcher matcher : wildcardMatchers) {
      if (matcher.match(path)) {
        String mapping = matcher.getMapping();
        // for jsp return servlet path
        if ("/*.jsp".equals(mapping) || "/*.jspx".equals(mapping)) {
          return servletPath;
        }
        return mapping;
      }
    }

    if (hasDefault) {
      return path.equals("/") ? "/" : "/*";
    }

    return null;
  }

  private interface WildcardMatcher {
    boolean match(String path);

    String getMapping();
  }

  private static class PrefixMatcher implements WildcardMatcher {
    private final String mapping;
    private final String prefix;

    private PrefixMatcher(String mapping, String prefix) {
      this.mapping = mapping;
      this.prefix = prefix;
    }

    @Override
    public boolean match(String path) {
      return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    @Override
    public String getMapping() {
      return mapping;
    }
  }

  private static class SuffixMatcher implements WildcardMatcher {
    private final String mapping;
    private final String suffix;

    private SuffixMatcher(String mapping, String suffix) {
      this.mapping = mapping;
      this.suffix = suffix;
    }

    @Override
    public boolean match(String path) {
      return path.endsWith(suffix);
    }

    @Override
    public String getMapping() {
      return mapping;
    }
  }

  /**
   * Factory interface for creating {@link MappingResolver} instances. The main reason this class is
   * here is that we need to ensure that the class used for {@code InstrumentationContext} lookup
   * is always the same. If we would use an injected class it could be different in different class
   * loaders.
   */
  public interface Factory {

    @Nullable
    MappingResolver get();
  }
}
