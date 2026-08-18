/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.rest.common.v1_0;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A single OpenSearch REST route. It pairs an operation name (such as {@code index} or {@code
 * search}) with a parameterized path template (such as {@code /{index}/_doc/{id}}) and compiles the
 * template into a regular expression with named capture groups for the path parameters.
 *
 * <p>Modeled on {@code ElasticsearchEndpointDefinition.Route} and its {@code EndpointPattern} in
 * the Elasticsearch instrumentation, but kept entirely inside the OpenSearch module so it carries
 * no cross-family dependency. Underscores in a capture group name are replaced with {@code 0} while
 * building the regex, because a Java named capture group disallows underscores.
 */
final class OpenSearchEndpointRoute {

  private static final String UNDERSCORE_REPLACEMENT = "0";
  private static final Pattern PATH_PART_NAMES_PATTERN = Pattern.compile("\\{([^}]+)}");

  private final String operationName;
  private final String template;
  private final Pattern pattern;
  private final List<String> pathPartNames;
  private final Set<String> structuralGroups;

  OpenSearchEndpointRoute(String operationName, String template) {
    this.operationName = operationName;
    this.template = template;
    this.pattern = buildRegexPattern(template);
    this.pathPartNames = new ArrayList<>();
    this.structuralGroups = new HashSet<>();
    Matcher matcher = PATH_PART_NAMES_PATTERN.matcher(template);
    while (matcher.find()) {
      String name = matcher.group(1);
      String groupName = name.replace("_", UNDERSCORE_REPLACEMENT);
      pathPartNames.add(groupName);
      if (isStructural(name)) {
        structuralGroups.add(groupName);
      }
    }
  }

  String getOperationName() {
    return operationName;
  }

  String getTemplate() {
    return template;
  }

  List<String> getPathPartNames() {
    return pathPartNames;
  }

  boolean isStructuralGroup(String groupName) {
    return structuralGroups.contains(groupName);
  }

  boolean matches(String path) {
    return pattern.matcher(path).matches();
  }

  /**
   * Rebuilds the path with every path parameter segment replaced by {@code maskedValue}, keeping
   * the endpoint structure (index names and API keywords) intact. Returns {@code null} when the
   * path does not match this route.
   */
  String maskPathParameters(String path, String maskedValue) {
    Matcher matcher = pattern.matcher(path);
    if (!matcher.matches()) {
      return null;
    }
    StringBuilder result = new StringBuilder(path);
    // rewrite from the end so earlier group offsets stay valid
    for (int i = pathPartNames.size() - 1; i >= 0; i--) {
      String group = pathPartNames.get(i);
      if (structuralGroups.contains(group)) {
        // index names and legacy mapping types are endpoint structure, not customer identifiers
        continue;
      }
      result.replace(matcher.start(group), matcher.end(group), maskedValue);
    }
    return result.toString();
  }

  /** Builds a regex pattern from the parameterized route template. */
  private static Pattern buildRegexPattern(String template) {
    StringBuilder regex = new StringBuilder();
    regex.append('^');
    int startIdx = template.indexOf('{');
    while (startIdx >= 0) {
      regex.append(template, 0, startIdx);

      int endIndex = template.indexOf('}');
      if (endIndex <= startIdx + 1) {
        break;
      }

      // Append a named capture group. An underscore in the group name is replaced with `0`,
      // because `_` is not allowed in a Java named capture group.
      String groupName = template.substring(startIdx + 1, endIndex);
      regex.append("(?<");
      regex.append(groupName.replace("_", UNDERSCORE_REPLACEMENT));
      // An index name or a legacy mapping type cannot begin with `_`, so an {index} or {type}
      // parameter must not match a reserved path segment such as `_search` or `_doc`. This keeps a
      // generic template like /{index}/{type}/{id} from swallowing a keyword route such as
      // /{index}/_doc/{id}.
      regex.append(isStructural(groupName) ? ">[^_/][^/]*)" : ">[^/]+)");

      template = template.substring(endIndex + 1);
      startIdx = template.indexOf('{');
    }

    regex.append(template);
    regex.append('$');

    return Pattern.compile(regex.toString());
  }

  private static boolean isStructural(String groupName) {
    return "index".equals(groupName) || "type".equals(groupName);
  }
}
