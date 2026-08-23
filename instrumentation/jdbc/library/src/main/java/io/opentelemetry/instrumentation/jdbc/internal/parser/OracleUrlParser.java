/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal.parser;

import static io.opentelemetry.instrumentation.jdbc.internal.parser.UrlParsingUtils.parsePort;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for Oracle JDBC URLs.
 *
 * <p>Sample URLs:
 *
 * <ul>
 *   <li>oracle:thin:@host:1521:orcl
 *   <li>oracle:thin:@host:1521/service
 *   <li>oracle:thin:user/pass@host/service
 *   <li>oracle:thin:@//host:1521/service
 *   <li>oracle:thin:@ldap://host:389/cn=OracleContext
 *   <li>oracle:thin:@(DESCRIPTION=(ADDRESS=(HOST=host)(PORT=1521))(CONNECT_DATA=(SERVICE_NAME=service)))
 * </ul>
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
@SuppressWarnings("deprecation") // supporting old semconv until 3.0
public final class OracleUrlParser implements JdbcUrlParser {

  // copied from DbIncubatingAttributes.DbSystemNameIncubatingValues
  private static final String ORACLE_DB = "oracle.db";
  // copied from DbIncubatingAttributes.DbSystemIncubatingValues
  private static final String ORACLE = "oracle";

  private static final int DEFAULT_PORT = 1521;

  // LIMITATION: Simple regex matching across entire DESCRIPTION may extract values from
  // non-primary ADDRESS blocks when the first block has incomplete specifications.
  private static final Pattern DESCRIPTION_PATTERN = Pattern.compile("@\\s*\\(\\s*description");
  private static final Pattern DESCRIPTION_LIST_PATTERN =
      Pattern.compile("\\(\\s*description_list\\s*=");
  private static final Pattern ADDRESS_LIST_PATTERN = Pattern.compile("\\(\\s*address_list\\s*=");
  private static final Pattern ADDRESS_PATTERN = Pattern.compile("\\(\\s*address\\s*=");
  private static final Pattern PROTOCOL_PATTERN =
      Pattern.compile("\\(\\s*protocol\\s*=\\s*([^ )]+)\\s*\\)");
  private static final Pattern HOST_PATTERN =
      Pattern.compile("\\(\\s*host\\s*=\\s*([^ )]+)\\s*\\)");
  private static final Pattern PORT_PATTERN =
      Pattern.compile("\\(\\s*port\\s*=\\s*([\\d]+)\\s*\\)");
  private static final Pattern SERVICE_NAME_PATTERN =
      Pattern.compile("\\(\\s*service_name\\s*=\\s*([^ )]+)\\s*\\)");

  public static final OracleUrlParser INSTANCE = new OracleUrlParser();

  private OracleUrlParser() {}

  @Override
  public void parse(String jdbcUrl, ParseContext ctx) {
    ctx.system(ORACLE_DB);
    ctx.oldSemconvSystem(ORACLE);
    ctx.port(DEFAULT_PORT);

    ctx.applyDataSourceProperties();

    // Parse the URL
    // URL format: oracle:<subtype>:<connect_info>
    int subtypeStart = "oracle:".length();
    int typeEndIndex = jdbcUrl.indexOf(":", subtypeStart);
    String subtype = jdbcUrl.substring(subtypeStart, typeEndIndex);
    String remainder = jdbcUrl.substring(typeEndIndex + 1);

    if (remainder.contains("@")) {
      parseAtFormat(remainder, ctx);
    } else {
      parseConnectInfo(remainder, ctx);
    }

    ctx.subtype(subtype);
  }

  private static void parseAtFormat(String jdbcUrl, ParseContext ctx) {
    if (DESCRIPTION_PATTERN.matcher(jdbcUrl).find()) {
      parseDescriptionFormat(jdbcUrl, ctx);
      return;
    }

    String[] atSplit = jdbcUrl.split("@", 2);

    // Check for user info before @
    int userInfoLoc = atSplit[0].indexOf("/");
    if (userInfoLoc > 0) {
      ctx.user(atSplit[0].substring(0, userInfoLoc));
    }

    String connectInfo = atSplit[1];
    int hostStart;
    if (connectInfo.startsWith("//")) {
      hostStart = "//".length();
    } else if (connectInfo.startsWith("ldap://")) {
      hostStart = "ldap://".length();
    } else {
      hostStart = 0;
    }

    parseConnectInfo(connectInfo.substring(hostStart), ctx);
  }

  /**
   * Parse Oracle DESCRIPTION format URLs.
   *
   * <p><b>LIMITATION:</b> When multiple ADDRESS blocks are present in the DESCRIPTION, this parser
   * uses simple regex matching that may extract values from non-primary address blocks. For
   * example, if the first ADDRESS block omits the port but a second ADDRESS block includes it, this
   * parser will incorrectly use the port from the second block. This is a known limitation that
   * affects multi-address configurations with incomplete first address specifications.
   */
  private static void parseDescriptionFormat(String jdbcUrl, ParseContext ctx) {
    String[] atSplit = jdbcUrl.split("@", 2);

    int userInfoLoc = atSplit[0].indexOf("/");
    if (userInfoLoc > 0) {
      ctx.user(atSplit[0].substring(0, userInfoLoc));
    }

    Matcher hostMatcher = HOST_PATTERN.matcher(atSplit[1]);
    if (hostMatcher.find()) {
      ctx.host(hostMatcher.group(1));
    }

    Matcher portMatcher = PORT_PATTERN.matcher(atSplit[1]);
    if (portMatcher.find()) {
      ctx.port(Integer.parseInt(portMatcher.group(1)));
    }

    Matcher instanceMatcher = SERVICE_NAME_PATTERN.matcher(atSplit[1]);
    if (instanceMatcher.find()) {
      ctx.databaseName(instanceMatcher.group(1));
    }

    applyAddressListGroup(atSplit[1], ctx);
  }

  /**
   * Keep the whole address list of a DESCRIPTION that names more than one ADDRESS, dropping the
   * CONNECT_DATA and every address attribute other than PROTOCOL, HOST and PORT.
   *
   * <p>A DESCRIPTION_LIST is left alone, because its addresses belong to separate descriptions and
   * flattening them into one list would misstate the configuration.
   */
  private static void applyAddressListGroup(String description, ParseContext ctx) {
    if (DESCRIPTION_LIST_PATTERN.matcher(description).find()) {
      return;
    }

    List<String> addresses = new ArrayList<>();
    Matcher addressMatcher = ADDRESS_PATTERN.matcher(description);
    int searchFrom = 0;
    while (addressMatcher.find(searchFrom)) {
      int end = findClosingParen(description, addressMatcher.start());
      if (end < 0) {
        return;
      }
      addresses.add(renderAddress(description.substring(addressMatcher.start(), end + 1)));
      searchFrom = end + 1;
    }
    if (addresses.size() < 2) {
      return;
    }

    StringBuilder group = new StringBuilder("@(description=");
    boolean addressList = ADDRESS_LIST_PATTERN.matcher(description).find();
    if (addressList) {
      group.append("(address_list=");
    }
    for (String address : addresses) {
      group.append(address);
    }
    if (addressList) {
      group.append(')');
    }
    group.append(')');
    ctx.serverAddressGroup(group.toString());
  }

  private static int findClosingParen(String text, int openParen) {
    int depth = 0;
    for (int i = openParen; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == '(') {
        depth++;
      } else if (c == ')') {
        depth--;
        if (depth == 0) {
          return i;
        }
      }
    }
    return -1;
  }

  private static String renderAddress(String address) {
    StringBuilder rendered = new StringBuilder("(address=");
    appendAttribute(rendered, "protocol", PROTOCOL_PATTERN, address);
    appendAttribute(rendered, "host", HOST_PATTERN, address);
    appendAttribute(rendered, "port", PORT_PATTERN, address);
    rendered.append(')');
    return rendered.toString();
  }

  private static void appendAttribute(
      StringBuilder rendered, String name, Pattern pattern, String address) {
    Matcher matcher = pattern.matcher(address);
    if (matcher.find()) {
      rendered.append('(').append(name).append('=').append(matcher.group(1)).append(')');
    }
  }

  private static void parseConnectInfo(String jdbcUrl, ParseContext ctx) {
    int hostEnd = jdbcUrl.indexOf(":");
    int instanceLoc = jdbcUrl.indexOf("/");

    // Case: no colon - just host, host/instance, or instance
    if (hostEnd <= 0) {
      if (instanceLoc > 0) {
        // host/instance (no port - keep default)
        ctx.host(jdbcUrl.substring(0, instanceLoc));
        ctx.databaseName(jdbcUrl.substring(instanceLoc + 1));
      } else if (!jdbcUrl.isEmpty()) {
        // Just instance name - keep default host/port
        ctx.databaseName(jdbcUrl);
      }
      return;
    }

    // From here: hostEnd > 0, so we have host:something
    ctx.host(jdbcUrl.substring(0, hostEnd));

    int afterHostEnd = jdbcUrl.indexOf(":", hostEnd + 1);
    if (afterHostEnd > 0) {
      // host:port:instance
      ctx.port(parsePort(jdbcUrl.substring(hostEnd + 1, afterHostEnd)));
      ctx.databaseName(jdbcUrl.substring(afterHostEnd + 1));
      return;
    }

    if (instanceLoc > 0) {
      // host:port/instance
      ctx.port(parsePort(jdbcUrl.substring(hostEnd + 1, instanceLoc)));
      ctx.databaseName(jdbcUrl.substring(instanceLoc + 1));
      return;
    }

    // host:portOrInstance - could be port or instance name
    String portOrInstance = jdbcUrl.substring(hostEnd + 1);
    Integer parsedPort = parsePort(portOrInstance);
    if (parsedPort != null) {
      ctx.port(parsedPort);
    } else {
      // It's an instance name, not a port - keep default port
      ctx.databaseName(portOrInstance);
    }
  }
}
