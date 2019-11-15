package datadog.trace.instrumentation.jdbc;

import static datadog.trace.instrumentation.jdbc.DBInfo.DEFAULT;

import datadog.trace.bootstrap.ExceptionLogger;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Structured as an enum instead of a class hierarchy to allow iterating through the parsers
 * automatically without having to maintain a separate list of parsers.
 */
public enum JDBCConnectionUrlParser {
  GENERIC_URL_LIKE() {
    @Override
    DBInfo.Builder doParse(final String jdbcUrl, final DBInfo.Builder builder) {
      try {
        // Attempt generic parsing
        final URI uri = new URI(jdbcUrl);

        populateStandardProperties(builder, splitQuery(uri.getQuery(), "&"));

        final String user = uri.getUserInfo();
        if (user != null) {
          builder.user(user);
        }

        String path = uri.getPath();
        if (path.startsWith("/")) {
          path = path.substring(1);
        }
        if (!path.isEmpty()) {
          builder.db(path);
        }

        if (uri.getHost() != null) {
          builder.host(uri.getHost());
        }

        if (uri.getPort() > 0) {
          builder.port(uri.getPort());
        }

        return builder.type(uri.getScheme());
      } catch (final Exception e) {
        return builder;
      }
    }
  },

  MODIFIED_URL_LIKE() {
    @Override
    DBInfo.Builder doParse(final String jdbcUrl, final DBInfo.Builder builder) {
      final String type;
      String serverName = "";
      Integer port = null;
      String instanceName = null;
      final String user = null;

      final int hostIndex = jdbcUrl.indexOf("://");

      if (hostIndex <= 0) {
        return builder;
      }

      type = jdbcUrl.substring(0, hostIndex);

      final String[] split;
      if (type.equals("db2") || type.equals("as400")) {
        if (jdbcUrl.contains("=")) {
          final int paramLoc = jdbcUrl.lastIndexOf(":");
          split = new String[] {jdbcUrl.substring(0, paramLoc), jdbcUrl.substring(paramLoc + 1)};
        } else {
          split = new String[] {jdbcUrl};
        }
      } else {
        split = jdbcUrl.split(";", 2);
      }

      if (split.length > 1) {
        final Map<String, String> props = splitQuery(split[1], ";");
        populateStandardProperties(builder, props);
        if (props.containsKey("servername")) {
          serverName = props.get("servername");
        }
      }

      final String urlServerName = split[0].substring(hostIndex + 3);
      if (!urlServerName.isEmpty()) {
        serverName = urlServerName;
      }

      int instanceLoc = serverName.indexOf("/");
      if (instanceLoc > 1) {
        instanceName = serverName.substring(instanceLoc + 1);
        serverName = serverName.substring(0, instanceLoc);
      }

      final int portLoc = serverName.indexOf(":");

      if (portLoc > 1) {
        port = Integer.parseInt(serverName.substring(portLoc + 1));
        serverName = serverName.substring(0, portLoc);
      }

      instanceLoc = serverName.indexOf("\\");
      if (instanceLoc > 1) {
        instanceName = serverName.substring(instanceLoc + 1);
        serverName = serverName.substring(0, instanceLoc);
      }

      if (instanceName != null) {
        builder.instance(instanceName);
      }

      if (!serverName.isEmpty()) {
        builder.host(serverName);
      }

      if (port != null) {
        builder.port(port);
      }

      if (user != null) {
        builder.user(user);
      }

      return builder.type(type);
    }
  },

  POSTGRES("postgresql") {
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 5432;

    @Override
    DBInfo.Builder doParse(final String jdbcUrl, final DBInfo.Builder builder) {
      final DBInfo dbInfo = builder.build();
      if (dbInfo.getHost() == null) {
        builder.host(DEFAULT_HOST);
      }
      if (dbInfo.getPort() == null) {
        builder.port(DEFAULT_PORT);
      }
      return GENERIC_URL_LIKE.doParse(jdbcUrl, builder);
    }
  },

  MYSQL("mysql", "mariadb") {
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 3306;

    @Override
    DBInfo.Builder doParse(final String jdbcUrl, final DBInfo.Builder builder) {
      final DBInfo dbInfo = builder.build();
      if (dbInfo.getHost() == null) {
        builder.host(DEFAULT_HOST);
      }
      if (dbInfo.getPort() == null) {
        builder.port(DEFAULT_PORT);
      }
      final int protoLoc = jdbcUrl.indexOf("://");
      final int typeEndLoc = dbInfo.getType().length();
      if (protoLoc > typeEndLoc) {
        return MARIA_SUBPROTO
            .doParse(jdbcUrl.substring(protoLoc + 3), builder)
            .subtype(jdbcUrl.substring(typeEndLoc + 1, protoLoc));
      }
      if (protoLoc > 0) {
        return GENERIC_URL_LIKE.doParse(jdbcUrl, builder);
      }

      final int hostEndLoc;
      final int portLoc = jdbcUrl.indexOf(":", typeEndLoc + 1);
      final int dbLoc = jdbcUrl.indexOf("/", typeEndLoc);
      final int paramLoc = jdbcUrl.indexOf("?", dbLoc);

      if (paramLoc > 0) {
        populateStandardProperties(builder, splitQuery(jdbcUrl.substring(paramLoc + 1), "&"));
        builder.db(jdbcUrl.substring(dbLoc + 1, paramLoc));
      } else {
        builder.db(jdbcUrl.substring(dbLoc + 1));
      }

      if (portLoc > 0) {
        hostEndLoc = portLoc;
        try {
          builder.port(Integer.parseInt(jdbcUrl.substring(portLoc + 1, dbLoc)));
        } catch (final NumberFormatException e) {
        }
      } else {
        hostEndLoc = dbLoc;
      }

      builder.host(jdbcUrl.substring(typeEndLoc + 1, hostEndLoc));

      return builder;
    }
  },

  MARIA_SUBPROTO() {
    @Override
    DBInfo.Builder doParse(final String jdbcUrl, final DBInfo.Builder builder) {
      final int hostEndLoc;
      final int clusterSepLoc = jdbcUrl.indexOf(",");
      final int ipv6End = jdbcUrl.startsWith("[") ? jdbcUrl.indexOf("]") : -1;
      int portLoc = jdbcUrl.indexOf(":", Math.max(0, ipv6End));
      portLoc = clusterSepLoc < portLoc ? -1 : portLoc;
      final int dbLoc = jdbcUrl.indexOf("/", Math.max(portLoc, clusterSepLoc));

      final int paramLoc = jdbcUrl.indexOf("?", dbLoc);

      if (paramLoc > 0) {
        populateStandardProperties(builder, splitQuery(jdbcUrl.substring(paramLoc + 1), "&"));
        builder.db(jdbcUrl.substring(dbLoc + 1, paramLoc));
      } else {
        builder.db(jdbcUrl.substring(dbLoc + 1));
      }

      if (jdbcUrl.startsWith("address=")) {
        return MARIA_ADDRESS.doParse(jdbcUrl, builder);
      }

      if (portLoc > 0) {
        hostEndLoc = portLoc;
        final int portEndLoc = clusterSepLoc > 0 ? clusterSepLoc : dbLoc;
        try {
          builder.port(Integer.parseInt(jdbcUrl.substring(portLoc + 1, portEndLoc)));
        } catch (final NumberFormatException e) {
        }
      } else {
        hostEndLoc = clusterSepLoc > 0 ? clusterSepLoc : dbLoc;
      }

      if (ipv6End > 0) {
        builder.host(jdbcUrl.substring(1, ipv6End));
      } else {
        builder.host(jdbcUrl.substring(0, hostEndLoc));
      }
      return builder;
    }
  },

  MARIA_ADDRESS() {
    private final Pattern HOST_REGEX = Pattern.compile("\\(\\s*host\\s*=\\s*([^ )]+)\\s*\\)");
    private final Pattern PORT_REGEX = Pattern.compile("\\(\\s*port\\s*=\\s*([\\d]+)\\s*\\)");
    private final Pattern USER_REGEX = Pattern.compile("\\(\\s*user\\s*=\\s*([^ )]+)\\s*\\)");

    @Override
    DBInfo.Builder doParse(String jdbcUrl, final DBInfo.Builder builder) {
      final int addressEnd = jdbcUrl.indexOf(",address=");
      if (addressEnd > 0) {
        jdbcUrl = jdbcUrl.substring(0, addressEnd);
      }
      final Matcher hostMatcher = HOST_REGEX.matcher(jdbcUrl);
      if (hostMatcher.find()) {
        builder.host(hostMatcher.group(1));
      }

      final Matcher portMatcher = PORT_REGEX.matcher(jdbcUrl);
      if (portMatcher.find()) {
        builder.port(Integer.parseInt(portMatcher.group(1)));
      }

      final Matcher userMatcher = USER_REGEX.matcher(jdbcUrl);
      if (userMatcher.find()) {
        builder.user(userMatcher.group(1));
      }

      return builder;
    }
  },

  SAP("sap") {
    private static final String DEFAULT_HOST = "localhost";

    @Override
    DBInfo.Builder doParse(final String jdbcUrl, final DBInfo.Builder builder) {
      final DBInfo dbInfo = builder.build();
      if (dbInfo.getHost() == null) {
        builder.host(DEFAULT_HOST);
      }
      return GENERIC_URL_LIKE.doParse(jdbcUrl, builder);
    }
  },

  MSSQLSERVER("microsoft", "sqlserver") {
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 1433;

    @Override
    DBInfo.Builder doParse(String jdbcUrl, final DBInfo.Builder builder) {
      if (jdbcUrl.startsWith("microsoft:")) {
        jdbcUrl = jdbcUrl.substring("microsoft:".length());
      }
      if (!jdbcUrl.startsWith("sqlserver://")) {
        return builder;
      }
      builder.type("sqlserver");
      final DBInfo dbInfo = builder.build();
      if (dbInfo.getHost() == null) {
        builder.host(DEFAULT_HOST);
      }
      if (dbInfo.getPort() == null) {
        builder.port(DEFAULT_PORT);
      }
      return MODIFIED_URL_LIKE.doParse(jdbcUrl, builder);
    }
  },

  DB2("db2", "as400") {
    private static final int DEFAULT_PORT = 50000;

    @Override
    DBInfo.Builder doParse(final String jdbcUrl, final DBInfo.Builder builder) {
      final DBInfo dbInfo = builder.build();
      if (dbInfo.getPort() == null) {
        builder.port(DEFAULT_PORT);
      }
      return MODIFIED_URL_LIKE.doParse(jdbcUrl, builder);
    }
  },

  ORACLE("oracle") {
    private static final int DEFAULT_PORT = 1521;

    @Override
    DBInfo.Builder doParse(String jdbcUrl, final DBInfo.Builder builder) {
      final int typeEndIndex = jdbcUrl.indexOf(":", "oracle:".length());
      final String subtype = jdbcUrl.substring("oracle:".length(), typeEndIndex);
      jdbcUrl = jdbcUrl.substring(typeEndIndex + 1);

      builder.subtype(subtype);
      final DBInfo dbInfo = builder.build();
      if (dbInfo.getPort() == null) {
        builder.port(DEFAULT_PORT);
      }

      if (jdbcUrl.contains("@")) {
        return ORACLE_AT.doParse(jdbcUrl, builder);
      } else {
        return ORACLE_CONNECT_INFO.doParse(jdbcUrl, builder);
      }
    }
  },

  ORACLE_CONNECT_INFO() {
    @Override
    DBInfo.Builder doParse(final String jdbcUrl, final DBInfo.Builder builder) {

      final String host;
      final Integer port;
      final String instance;

      final int hostEnd = jdbcUrl.indexOf(":");
      final int instanceLoc = jdbcUrl.indexOf("/");
      if (hostEnd > 0) {
        host = jdbcUrl.substring(0, hostEnd);
        final int afterHostEnd = jdbcUrl.indexOf(":", hostEnd + 1);
        if (afterHostEnd > 0) {
          port = Integer.parseInt(jdbcUrl.substring(hostEnd + 1, afterHostEnd));
          instance = jdbcUrl.substring(afterHostEnd + 1);
        } else {
          if (instanceLoc > 0) {
            instance = jdbcUrl.substring(instanceLoc + 1);
            port = Integer.parseInt(jdbcUrl.substring(hostEnd + 1, instanceLoc));
          } else {
            final String portOrInstance = jdbcUrl.substring(hostEnd + 1);
            Integer parsedPort = null;
            try {
              parsedPort = Integer.parseInt(portOrInstance);
            } catch (final NumberFormatException e) {
            }
            if (parsedPort == null) {
              port = null;
              instance = portOrInstance;
            } else {
              port = parsedPort;
              instance = null;
            }
          }
        }
      } else {
        if (instanceLoc > 0) {
          host = jdbcUrl.substring(0, instanceLoc);
          port = null;
          instance = jdbcUrl.substring(instanceLoc + 1);
        } else {
          if (jdbcUrl.isEmpty()) {
            return builder;
          } else {
            host = null;
            port = null;
            instance = jdbcUrl;
          }
        }
      }
      if (host != null) {
        builder.host(host);
      }
      if (port != null) {
        builder.port(port);
      }
      return builder.instance(instance);
    }
  },

  ORACLE_AT() {
    @Override
    DBInfo.Builder doParse(final String jdbcUrl, final DBInfo.Builder builder) {
      if (jdbcUrl.contains("@(description")) {
        return ORACLE_AT_DESCRIPTION.doParse(jdbcUrl, builder);
      }
      final String user;

      final String[] atSplit = jdbcUrl.split("@", 2);

      final int userInfoLoc = atSplit[0].indexOf("/");
      if (userInfoLoc > 0) {
        user = atSplit[0].substring(0, userInfoLoc);
      } else {
        user = null;
      }

      final String connectInfo = atSplit[1];
      final int hostStart;
      if (connectInfo.startsWith("//")) {
        hostStart = "//".length();
      } else if (connectInfo.startsWith("ldap://")) {
        hostStart = "ldap://".length();
      } else {
        hostStart = 0;
      }
      if (user != null) {
        builder.user(user);
      }
      return ORACLE_CONNECT_INFO.doParse(connectInfo.substring(hostStart), builder);
    }
  },

  /**
   * This parser can locate incorrect data if multiple addresses are defined but not everything is
   * defined in the first block. (It would locate data from subsequent address blocks.
   */
  ORACLE_AT_DESCRIPTION() {
    private final Pattern HOST_REGEX = Pattern.compile("\\(\\s*host\\s*=\\s*([^ )]+)\\s*\\)");
    private final Pattern PORT_REGEX = Pattern.compile("\\(\\s*port\\s*=\\s*([\\d]+)\\s*\\)");
    private final Pattern INSTANCE_REGEX =
        Pattern.compile("\\(\\s*service_name\\s*=\\s*([^ )]+)\\s*\\)");

    @Override
    DBInfo.Builder doParse(final String jdbcUrl, final DBInfo.Builder builder) {
      final String[] atSplit = jdbcUrl.split("@", 2);

      final int userInfoLoc = atSplit[0].indexOf("/");
      if (userInfoLoc > 0) {
        builder.user(atSplit[0].substring(0, userInfoLoc));
      }

      final Matcher hostMatcher = HOST_REGEX.matcher(atSplit[1]);
      if (hostMatcher.find()) {
        builder.host(hostMatcher.group(1));
      }

      final Matcher portMatcher = PORT_REGEX.matcher(atSplit[1]);
      if (portMatcher.find()) {
        builder.port(Integer.parseInt(portMatcher.group(1)));
      }

      final Matcher instanceMatcher = INSTANCE_REGEX.matcher(atSplit[1]);
      if (instanceMatcher.find()) {
        builder.instance(instanceMatcher.group(1));
      }

      return builder;
    }
  },

  H2("h2") {
    private static final int DEFAULT_PORT = 8082;

    @Override
    DBInfo.Builder doParse(final String jdbcUrl, final DBInfo.Builder builder) {
      final String instance;

      final String h2Url = jdbcUrl.substring("h2:".length());
      if (h2Url.startsWith("mem:")) {
        builder.subtype("mem");
        final int propLoc = h2Url.indexOf(";");
        if (propLoc >= 0) {
          instance = h2Url.substring("mem:".length(), propLoc);
        } else {
          instance = h2Url.substring("mem:".length());
        }
      } else if (h2Url.startsWith("file:")) {
        builder.subtype("file");
        final int propLoc = h2Url.indexOf(";");
        if (propLoc >= 0) {
          instance = h2Url.substring("file:".length(), propLoc);
        } else {
          instance = h2Url.substring("file:".length());
        }
      } else if (h2Url.startsWith("zip:")) {
        builder.subtype("zip");
        final int propLoc = h2Url.indexOf(";");
        if (propLoc >= 0) {
          instance = h2Url.substring("zip:".length(), propLoc);
        } else {
          instance = h2Url.substring("zip:".length());
        }
      } else if (h2Url.startsWith("tcp:")) {
        final DBInfo dbInfo = builder.build();
        if (dbInfo.getPort() == null) {
          builder.port(DEFAULT_PORT);
        }
        return MODIFIED_URL_LIKE.doParse(jdbcUrl, builder).type("h2").subtype("tcp");
      } else if (h2Url.startsWith("ssl:")) {
        final DBInfo dbInfo = builder.build();
        if (dbInfo.getPort() == null) {
          builder.port(DEFAULT_PORT);
        }
        return MODIFIED_URL_LIKE.doParse(jdbcUrl, builder).type("h2").subtype("ssl");
      } else {
        builder.subtype("file");
        final int propLoc = h2Url.indexOf(";");
        if (propLoc >= 0) {
          instance = h2Url.substring(0, propLoc);
        } else {
          instance = h2Url;
        }
      }
      if (!instance.isEmpty()) {
        builder.instance(instance);
      }
      return builder;
    }
  },

  HSQL("hsqldb") {
    private static final String DEFAULT_USER = "SA";
    private static final int DEFAULT_PORT = 9001;

    @Override
    DBInfo.Builder doParse(final String jdbcUrl, final DBInfo.Builder builder) {
      String instance = null;
      final DBInfo dbInfo = builder.build();
      if (dbInfo.getUser() == null) {
        builder.user(DEFAULT_USER);
      }
      final String hsqlUrl = jdbcUrl.substring("hsqldb:".length());
      if (hsqlUrl.startsWith("mem:")) {
        builder.subtype("mem");
        instance = hsqlUrl.substring("mem:".length());
      } else if (hsqlUrl.startsWith("file:")) {
        builder.subtype("file");
        instance = hsqlUrl.substring("file:".length());
      } else if (hsqlUrl.startsWith("res:")) {
        builder.subtype("res");
        instance = hsqlUrl.substring("res:".length());
      } else if (hsqlUrl.startsWith("hsql:")) {
        if (dbInfo.getPort() == null) {
          builder.port(DEFAULT_PORT);
        }
        return MODIFIED_URL_LIKE.doParse(jdbcUrl, builder).type("hsqldb").subtype("hsql");
      } else if (hsqlUrl.startsWith("hsqls:")) {
        if (dbInfo.getPort() == null) {
          builder.port(DEFAULT_PORT);
        }
        return MODIFIED_URL_LIKE.doParse(jdbcUrl, builder).type("hsqldb").subtype("hsqls");
      } else if (hsqlUrl.startsWith("http:")) {
        if (dbInfo.getPort() == null) {
          builder.port(80);
        }
        return MODIFIED_URL_LIKE.doParse(jdbcUrl, builder).type("hsqldb").subtype("http");
      } else if (hsqlUrl.startsWith("https:")) {
        if (dbInfo.getPort() == null) {
          builder.port(443);
        }
        return MODIFIED_URL_LIKE.doParse(jdbcUrl, builder).type("hsqldb").subtype("https");
      } else {
        builder.subtype("mem");
        instance = hsqlUrl;
      }
      return builder.instance(instance);
    }
  },

  DERBY("derby") {
    private static final String DEFAULT_USER = "APP";
    private static final int DEFAULT_PORT = 1527;

    @Override
    DBInfo.Builder doParse(final String jdbcUrl, final DBInfo.Builder builder) {
      String instance = null;
      String host = null;

      final DBInfo dbInfo = builder.build();
      if (dbInfo.getUser() == null) {
        builder.user(DEFAULT_USER);
      }

      final String derbyUrl = jdbcUrl.substring("derby:".length());
      final String[] split = derbyUrl.split(";", 2);

      if (split.length > 1) {
        populateStandardProperties(builder, splitQuery(split[1], ";"));
      }

      final String details = split[0];
      if (details.startsWith("memory:")) {
        builder.subtype("memory");
        final String urlInstance = details.substring("memory:".length());
        if (!urlInstance.isEmpty()) {
          instance = urlInstance;
        }
      } else if (details.startsWith("directory:")) {
        builder.subtype("directory");
        final String urlInstance = details.substring("directory:".length());
        if (!urlInstance.isEmpty()) {
          instance = urlInstance;
        }
      } else if (details.startsWith("classpath:")) {
        builder.subtype("classpath");
        final String urlInstance = details.substring("classpath:".length());
        if (!urlInstance.isEmpty()) {
          instance = urlInstance;
        }
      } else if (details.startsWith("jar:")) {
        builder.subtype("jar");
        final String urlInstance = details.substring("jar:".length());
        if (!urlInstance.isEmpty()) {
          instance = urlInstance;
        }
      } else if (details.startsWith("//")) {
        builder.subtype("network");
        if (dbInfo.getPort() == null) {
          builder.port(DEFAULT_PORT);
        }
        String url = details.substring("//".length());
        final int instanceLoc = url.indexOf("/");
        if (instanceLoc >= 0) {
          instance = url.substring(instanceLoc + 1);
          final int protoLoc = instance.indexOf(":");
          if (protoLoc >= 0) {
            instance = instance.substring(protoLoc + 1);
          }
          url = url.substring(0, instanceLoc);
        }
        final int portLoc = url.indexOf(":");
        if (portLoc > 0) {
          host = url.substring(0, portLoc);
          builder.port(Integer.parseInt(url.substring(portLoc + 1)));
        } else {
          host = url;
        }
      } else {
        builder.subtype("directory");
        final String urlInstance = details;
        if (!urlInstance.isEmpty()) {
          instance = urlInstance;
        }
      }

      if (host != null) {
        builder.host(host);
      }
      return builder.instance(instance);
    }
  };

  private static final Map<String, JDBCConnectionUrlParser> typeParsers = new HashMap<>();

  static {
    for (final JDBCConnectionUrlParser parser : JDBCConnectionUrlParser.values()) {
      for (final String key : parser.typeKeys) {
        typeParsers.put(key, parser);
      }
    }
  }

  private final String[] typeKeys;

  JDBCConnectionUrlParser(final String... typeKeys) {
    this.typeKeys = typeKeys;
  }

  abstract DBInfo.Builder doParse(String jdbcUrl, final DBInfo.Builder builder);

  public static DBInfo parse(String connectionUrl, final Properties props) {
    if (connectionUrl == null) {
      return DEFAULT;
    }
    // Make this easier and ignore case.
    connectionUrl = connectionUrl.toLowerCase();

    if (!connectionUrl.startsWith("jdbc:")) {
      return DEFAULT;
    }

    final String jdbcUrl = connectionUrl.substring("jdbc:".length());
    final int typeLoc = jdbcUrl.indexOf(':');

    if (typeLoc < 1) {
      // Invalid format: `jdbc:` or `jdbc::`
      return DEFAULT;
    }

    final String baseType = jdbcUrl.substring(0, typeLoc);

    final DBInfo.Builder parsedProps = DEFAULT.toBuilder().type(baseType);
    populateStandardProperties(parsedProps, props);

    try {
      if (typeParsers.containsKey(baseType)) {
        // Delegate to specific parser
        return typeParsers.get(baseType).doParse(jdbcUrl, parsedProps).build();
      }
      return GENERIC_URL_LIKE.doParse(connectionUrl, parsedProps).build();
    } catch (final Exception e) {
      ExceptionLogger.LOGGER.debug("Error parsing URL", e);
      return parsedProps.build();
    }
  }

  // Source: https://stackoverflow.com/a/13592567
  private static Map<String, String> splitQuery(final String query, final String separator) {
    if (query == null || query.isEmpty()) {
      return Collections.emptyMap();
    }
    final Map<String, String> query_pairs = new LinkedHashMap<>();
    final String[] pairs = query.split(separator);
    for (final String pair : pairs) {
      try {
        final int idx = pair.indexOf("=");
        final String key = idx > 0 ? URLDecoder.decode(pair.substring(0, idx), "UTF-8") : pair;
        if (!query_pairs.containsKey(key)) {
          final String value =
              idx > 0 && pair.length() > idx + 1
                  ? URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                  : null;
          query_pairs.put(key, value);
        }
      } catch (final UnsupportedEncodingException e) {
        // Ignore.
      }
    }
    return query_pairs;
  }

  private static void populateStandardProperties(
      final DBInfo.Builder builder, final Map<? extends Object, ? extends Object> props) {
    if (props != null && !props.isEmpty()) {
      if (props.containsKey("user")) {
        builder.user((String) props.get("user"));
      }

      if (props.containsKey("databasename")) {
        builder.db((String) props.get("databasename"));
      }
      if (props.containsKey("databaseName")) {
        builder.db((String) props.get("databaseName"));
      }

      if (props.containsKey("servername")) {
        builder.host((String) props.get("servername"));
      }
      if (props.containsKey("serverName")) {
        builder.host((String) props.get("serverName"));
      }

      if (props.containsKey("portnumber")) {
        final String portNumber = (String) props.get("portnumber");
        try {
          builder.port(Integer.parseInt(portNumber));
        } catch (final NumberFormatException e) {
          ExceptionLogger.LOGGER.debug("Error parsing portnumber property: " + portNumber, e);
        }
      }

      if (props.containsKey("portNumber")) {
        final String portNumber = (String) props.get("portNumber");
        try {
          builder.port(Integer.parseInt(portNumber));
        } catch (final NumberFormatException e) {
          ExceptionLogger.LOGGER.debug("Error parsing portNumber property: " + portNumber, e);
        }
      }
    }
  }
}
