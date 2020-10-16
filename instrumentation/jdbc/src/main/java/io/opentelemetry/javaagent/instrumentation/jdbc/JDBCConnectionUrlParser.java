/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jdbc;

import static io.opentelemetry.javaagent.instrumentation.jdbc.DBInfo.DEFAULT;
import static java.util.regex.Pattern.CASE_INSENSITIVE;

import io.opentelemetry.javaagent.instrumentation.api.jdbc.DbSystem;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Structured as an enum instead of a class hierarchy to allow iterating through the parsers
 * automatically without having to maintain a separate list of parsers.
 */
public enum JDBCConnectionUrlParser {
  GENERIC_URL_LIKE() {
    @Override
    DBInfo.Builder doParse(String jdbcUrl, DBInfo.Builder builder) {
      try {
        // Attempt generic parsing
        URI uri = new URI(jdbcUrl);

        populateStandardProperties(builder, splitQuery(uri.getQuery(), "&"));

        String user = uri.getUserInfo();
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

        return builder.system(uri.getScheme());
      } catch (Exception e) {
        return builder;
      }
    }
  },

  /**
   * http://jtds.sourceforge.net/faq.html#urlFormat
   * jdbc:jtds:<server_type>://<server>[:<port>][/<database>][;<property>=<value>[;...]]
   */
  JTDS_URL_LIKE() {
    @Override
    DBInfo.Builder doParse(String jdbcUrl, DBInfo.Builder builder) {
      String serverName = "";
      Integer port = null;

      int hostIndex = jdbcUrl.indexOf("jtds:sqlserver://");
      if (hostIndex < 0) {
        return builder;
      }

      String[] split = jdbcUrl.split(";", 2);
      if (split.length > 1) {
        Map<String, String> props = splitQuery(split[1], ";");
        populateStandardProperties(builder, props);
        if (props.containsKey("instance")) {
          builder.name(props.get("instance"));
        }
      }

      String urlServerName = split[0].substring(hostIndex + 17);
      if (!urlServerName.isEmpty()) {
        serverName = urlServerName;
      }

      int databaseLoc = serverName.indexOf("/");
      if (databaseLoc > 1) {
        builder.db(serverName.substring(databaseLoc + 1));
        serverName = serverName.substring(0, databaseLoc);
      }

      int portLoc = serverName.indexOf(":");
      if (portLoc > 1) {
        builder.port(Integer.parseInt(serverName.substring(portLoc + 1)));
        serverName = serverName.substring(0, portLoc);
      }

      if (!serverName.isEmpty()) {
        builder.host(serverName);
      }

      return builder;
    }
  },

  MODIFIED_URL_LIKE() {
    // Source: Regular Expressions Cookbook 2nd edition - 8.17.
    // Matches Standard, Mixed or Compressed notation in a wider body of text
    private final Pattern IPv6 =
        Pattern.compile(
            "(?:(?:(?:[A-F0-9]{1,4}:){6}"
                + "|(?=(?:[A-F0-9]{0,4}:){0,6}(?:[0-9]{1,3}\\.){3}[0-9]{1,3}(?![:.\\w]))"
                + "(([0-9A-F]{1,4}:){0,5}|:)((:[0-9A-F]{1,4}){1,5}:|:)|::(?:[A-F0-9]{1,4}:){5})"
                + "(?:(?:25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9]?[0-9])\\.){3}"
                + "(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)|(?:[A-F0-9]{1,4}:){7}"
                + "[A-F0-9]{1,4}|(?=(?:[A-F0-9]{0,4}:){0,7}[A-F0-9]{0,4}(?![:.\\w]))"
                + "(([0-9A-F]{1,4}:){1,7}|:)((:[0-9A-F]{1,4}){1,7}|:)"
                + "|(?:[A-F0-9]{1,4}:){7}:|:(:[A-F0-9]{1,4}){7})(?![:.\\w])",
            CASE_INSENSITIVE);

    @Override
    DBInfo.Builder doParse(String jdbcUrl, DBInfo.Builder builder) {
      String type;
      String serverName = "";
      Integer port = null;
      String name = null;

      int hostIndex = jdbcUrl.indexOf("://");

      if (hostIndex <= 0) {
        return builder;
      }

      type = jdbcUrl.substring(0, hostIndex);

      String[] split;
      if (type.equals("db2") || type.equals("as400")) {
        if (jdbcUrl.contains("=")) {
          int paramLoc = jdbcUrl.lastIndexOf(":");
          split = new String[] {jdbcUrl.substring(0, paramLoc), jdbcUrl.substring(paramLoc + 1)};
        } else {
          split = new String[] {jdbcUrl};
        }
      } else {
        split = jdbcUrl.split(";", 2);
      }

      if (split.length > 1) {
        Map<String, String> props = splitQuery(split[1], ";");
        populateStandardProperties(builder, props);
        if (props.containsKey("servername")) {
          serverName = props.get("servername");
        }
      }

      String urlServerName = split[0].substring(hostIndex + 3);
      if (!urlServerName.isEmpty()) {
        serverName = urlServerName;
      }

      int instanceLoc = serverName.indexOf("/");
      if (instanceLoc > 1) {
        name = serverName.substring(instanceLoc + 1);
        serverName = serverName.substring(0, instanceLoc);
      }

      Matcher ipv6Matcher = IPv6.matcher(serverName);
      boolean isIpv6 = ipv6Matcher.find();

      int portLoc = -1;
      if (isIpv6) {
        if (serverName.startsWith("[")) {
          portLoc = serverName.indexOf("]:") + 1;
        } else {
          serverName = "[" + serverName + "]";
        }
      } else {
        portLoc = serverName.indexOf(":");
      }

      if (portLoc > 1) {
        port = Integer.parseInt(serverName.substring(portLoc + 1));
        serverName = serverName.substring(0, portLoc);
      }

      instanceLoc = serverName.indexOf("\\");
      if (instanceLoc > 1) {
        if (isIpv6) {
          name = serverName.substring(instanceLoc + 1, serverName.lastIndexOf(']'));
          serverName = "[" + ipv6Matcher.group(0) + "]";
        } else {
          name = serverName.substring(instanceLoc + 1);
          serverName = serverName.substring(0, instanceLoc);
        }
      }

      if (name != null) {
        builder.name(name);
      }

      if (!serverName.isEmpty()) {
        builder.host(serverName);
      }

      if (port != null) {
        builder.port(port);
      }

      return builder;
    }
  },

  POSTGRES("postgresql") {
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 5432;

    @Override
    DBInfo.Builder doParse(String jdbcUrl, DBInfo.Builder builder) {
      DBInfo dbInfo = builder.build();
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
    DBInfo.Builder doParse(String jdbcUrl, DBInfo.Builder builder) {
      DBInfo dbInfo = builder.build();
      if (dbInfo.getHost() == null) {
        builder.host(DEFAULT_HOST);
      }
      if (dbInfo.getPort() == null) {
        builder.port(DEFAULT_PORT);
      }

      int protoLoc = jdbcUrl.indexOf("://");
      int typeEndLoc = jdbcUrl.indexOf(':');
      if (typeEndLoc < protoLoc) {
        return MARIA_SUBPROTO
            .doParse(jdbcUrl.substring(protoLoc + 3), builder)
            .subtype(jdbcUrl.substring(typeEndLoc + 1, protoLoc));
      }
      if (protoLoc > 0) {
        return GENERIC_URL_LIKE.doParse(jdbcUrl, builder);
      }

      int hostEndLoc;
      int portLoc = jdbcUrl.indexOf(":", typeEndLoc + 1);
      int dbLoc = jdbcUrl.indexOf("/", typeEndLoc);
      int paramLoc = jdbcUrl.indexOf("?", dbLoc);

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
        } catch (NumberFormatException e) {
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
    DBInfo.Builder doParse(String jdbcUrl, DBInfo.Builder builder) {
      int hostEndLoc;
      int clusterSepLoc = jdbcUrl.indexOf(",");
      int ipv6End = jdbcUrl.startsWith("[") ? jdbcUrl.indexOf("]") : -1;
      int portLoc = jdbcUrl.indexOf(":", Math.max(0, ipv6End));
      portLoc = clusterSepLoc < portLoc ? -1 : portLoc;
      int dbLoc = jdbcUrl.indexOf("/", Math.max(portLoc, clusterSepLoc));

      int paramLoc = jdbcUrl.indexOf("?", dbLoc);

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
        int portEndLoc = clusterSepLoc > 0 ? clusterSepLoc : dbLoc;
        try {
          builder.port(Integer.parseInt(jdbcUrl.substring(portLoc + 1, portEndLoc)));
        } catch (NumberFormatException e) {
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
    DBInfo.Builder doParse(String jdbcUrl, DBInfo.Builder builder) {
      int addressEnd = jdbcUrl.indexOf(",address=");
      if (addressEnd > 0) {
        jdbcUrl = jdbcUrl.substring(0, addressEnd);
      }
      Matcher hostMatcher = HOST_REGEX.matcher(jdbcUrl);
      if (hostMatcher.find()) {
        builder.host(hostMatcher.group(1));
      }

      Matcher portMatcher = PORT_REGEX.matcher(jdbcUrl);
      if (portMatcher.find()) {
        builder.port(Integer.parseInt(portMatcher.group(1)));
      }

      Matcher userMatcher = USER_REGEX.matcher(jdbcUrl);
      if (userMatcher.find()) {
        builder.user(userMatcher.group(1));
      }

      return builder;
    }
  },

  SAP("sap") {
    private static final String DEFAULT_HOST = "localhost";

    @Override
    DBInfo.Builder doParse(String jdbcUrl, DBInfo.Builder builder) {
      DBInfo dbInfo = builder.build();
      if (dbInfo.getHost() == null) {
        builder.host(DEFAULT_HOST);
      }
      return GENERIC_URL_LIKE.doParse(jdbcUrl, builder);
    }
  },

  MSSQLSERVER("jtds", "microsoft", "sqlserver") {
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 1433;

    @Override
    DBInfo.Builder doParse(String jdbcUrl, DBInfo.Builder builder) {
      DBInfo dbInfo = builder.build();
      if (dbInfo.getHost() == null) {
        builder.host(DEFAULT_HOST);
      }
      if (dbInfo.getPort() == null) {
        builder.port(DEFAULT_PORT);
      }

      int protoLoc = jdbcUrl.indexOf("://");
      int typeEndLoc = jdbcUrl.indexOf(':');
      if (protoLoc > typeEndLoc) {
        String subtype = jdbcUrl.substring(typeEndLoc + 1, protoLoc);
        builder.subtype(subtype);
      }

      if (jdbcUrl.startsWith("jtds:")) {
        return JTDS_URL_LIKE.doParse(jdbcUrl, builder);
      }

      return MODIFIED_URL_LIKE.doParse(jdbcUrl, builder);
    }
  },

  DB2("db2", "as400") {
    private static final int DEFAULT_PORT = 50000;

    @Override
    DBInfo.Builder doParse(String jdbcUrl, DBInfo.Builder builder) {
      DBInfo dbInfo = builder.build();
      if (dbInfo.getPort() == null) {
        builder.port(DEFAULT_PORT);
      }
      return MODIFIED_URL_LIKE.doParse(jdbcUrl, builder);
    }
  },

  ORACLE("oracle") {
    private static final int DEFAULT_PORT = 1521;

    @Override
    DBInfo.Builder doParse(String jdbcUrl, DBInfo.Builder builder) {
      int typeEndIndex = jdbcUrl.indexOf(":", "oracle:".length());
      String subtype = jdbcUrl.substring("oracle:".length(), typeEndIndex);
      jdbcUrl = jdbcUrl.substring(typeEndIndex + 1);

      builder.subtype(subtype);
      DBInfo dbInfo = builder.build();
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
    DBInfo.Builder doParse(String jdbcUrl, DBInfo.Builder builder) {

      String host;
      Integer port;
      String instance;

      int hostEnd = jdbcUrl.indexOf(":");
      int instanceLoc = jdbcUrl.indexOf("/");
      if (hostEnd > 0) {
        host = jdbcUrl.substring(0, hostEnd);
        int afterHostEnd = jdbcUrl.indexOf(":", hostEnd + 1);
        if (afterHostEnd > 0) {
          port = Integer.parseInt(jdbcUrl.substring(hostEnd + 1, afterHostEnd));
          instance = jdbcUrl.substring(afterHostEnd + 1);
        } else {
          if (instanceLoc > 0) {
            instance = jdbcUrl.substring(instanceLoc + 1);
            port = Integer.parseInt(jdbcUrl.substring(hostEnd + 1, instanceLoc));
          } else {
            String portOrInstance = jdbcUrl.substring(hostEnd + 1);
            Integer parsedPort = null;
            try {
              parsedPort = Integer.parseInt(portOrInstance);
            } catch (NumberFormatException e) {
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
      return builder.name(instance);
    }
  },

  ORACLE_AT() {
    @Override
    DBInfo.Builder doParse(String jdbcUrl, DBInfo.Builder builder) {
      if (jdbcUrl.contains("@(description")) {
        return ORACLE_AT_DESCRIPTION.doParse(jdbcUrl, builder);
      }
      String user;

      String[] atSplit = jdbcUrl.split("@", 2);

      int userInfoLoc = atSplit[0].indexOf("/");
      if (userInfoLoc > 0) {
        user = atSplit[0].substring(0, userInfoLoc);
      } else {
        user = null;
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
    DBInfo.Builder doParse(String jdbcUrl, DBInfo.Builder builder) {
      String[] atSplit = jdbcUrl.split("@", 2);

      int userInfoLoc = atSplit[0].indexOf("/");
      if (userInfoLoc > 0) {
        builder.user(atSplit[0].substring(0, userInfoLoc));
      }

      Matcher hostMatcher = HOST_REGEX.matcher(atSplit[1]);
      if (hostMatcher.find()) {
        builder.host(hostMatcher.group(1));
      }

      Matcher portMatcher = PORT_REGEX.matcher(atSplit[1]);
      if (portMatcher.find()) {
        builder.port(Integer.parseInt(portMatcher.group(1)));
      }

      Matcher instanceMatcher = INSTANCE_REGEX.matcher(atSplit[1]);
      if (instanceMatcher.find()) {
        builder.name(instanceMatcher.group(1));
      }

      return builder;
    }
  },

  H2("h2") {
    private static final int DEFAULT_PORT = 8082;

    @Override
    DBInfo.Builder doParse(String jdbcUrl, DBInfo.Builder builder) {
      String instance;

      String h2Url = jdbcUrl.substring("h2:".length());
      if (h2Url.startsWith("mem:")) {
        builder.subtype("mem").host(null).port(null);
        int propLoc = h2Url.indexOf(";");
        if (propLoc >= 0) {
          instance = h2Url.substring("mem:".length(), propLoc);
        } else {
          instance = h2Url.substring("mem:".length());
        }
      } else if (h2Url.startsWith("file:")) {
        builder.subtype("file").host(null).port(null);
        int propLoc = h2Url.indexOf(";");
        if (propLoc >= 0) {
          instance = h2Url.substring("file:".length(), propLoc);
        } else {
          instance = h2Url.substring("file:".length());
        }
      } else if (h2Url.startsWith("zip:")) {
        builder.subtype("zip").host(null).port(null);
        int propLoc = h2Url.indexOf(";");
        if (propLoc >= 0) {
          instance = h2Url.substring("zip:".length(), propLoc);
        } else {
          instance = h2Url.substring("zip:".length());
        }
      } else if (h2Url.startsWith("tcp:")) {
        DBInfo dbInfo = builder.build();
        if (dbInfo.getPort() == null) {
          builder.port(DEFAULT_PORT);
        }
        return MODIFIED_URL_LIKE.doParse(jdbcUrl, builder).system(DbSystem.H2).subtype("tcp");
      } else if (h2Url.startsWith("ssl:")) {
        DBInfo dbInfo = builder.build();
        if (dbInfo.getPort() == null) {
          builder.port(DEFAULT_PORT);
        }
        return MODIFIED_URL_LIKE.doParse(jdbcUrl, builder).system(DbSystem.H2).subtype("ssl");
      } else {
        builder.subtype("file").host(null).port(null);
        int propLoc = h2Url.indexOf(";");
        if (propLoc >= 0) {
          instance = h2Url.substring(0, propLoc);
        } else {
          instance = h2Url;
        }
      }
      if (!instance.isEmpty()) {
        builder.name(instance);
      }
      return builder;
    }
  },

  HSQL("hsqldb") {
    private static final String DEFAULT_USER = "SA";
    private static final int DEFAULT_PORT = 9001;

    @Override
    DBInfo.Builder doParse(String jdbcUrl, DBInfo.Builder builder) {
      String instance = null;
      DBInfo dbInfo = builder.build();
      if (dbInfo.getUser() == null) {
        builder.user(DEFAULT_USER);
      }
      String hsqlUrl = jdbcUrl.substring("hsqldb:".length());
      int proIndex = hsqlUrl.indexOf(";");
      if (proIndex >= 0) {
        hsqlUrl = hsqlUrl.substring(0, proIndex);
      } else {
        int varIndex = hsqlUrl.indexOf("?");
        if (varIndex >= 0) {
          hsqlUrl = hsqlUrl.substring(0, varIndex);
        }
      }
      if (hsqlUrl.startsWith("mem:")) {
        builder.subtype("mem").host(null).port(null);
        instance = hsqlUrl.substring("mem:".length());
      } else if (hsqlUrl.startsWith("file:")) {
        builder.subtype("file").host(null).port(null);
        instance = hsqlUrl.substring("file:".length());
      } else if (hsqlUrl.startsWith("res:")) {
        builder.subtype("res").host(null).port(null);
        instance = hsqlUrl.substring("res:".length());
      } else if (hsqlUrl.startsWith("hsql:")) {
        if (dbInfo.getPort() == null) {
          builder.port(DEFAULT_PORT);
        }
        return MODIFIED_URL_LIKE.doParse(jdbcUrl, builder).system(DbSystem.HSQLDB).subtype("hsql");
      } else if (hsqlUrl.startsWith("hsqls:")) {
        if (dbInfo.getPort() == null) {
          builder.port(DEFAULT_PORT);
        }
        return MODIFIED_URL_LIKE.doParse(jdbcUrl, builder).system(DbSystem.HSQLDB).subtype("hsqls");
      } else if (hsqlUrl.startsWith("http:")) {
        if (dbInfo.getPort() == null) {
          builder.port(80);
        }
        return MODIFIED_URL_LIKE.doParse(jdbcUrl, builder).system(DbSystem.HSQLDB).subtype("http");
      } else if (hsqlUrl.startsWith("https:")) {
        if (dbInfo.getPort() == null) {
          builder.port(443);
        }
        return MODIFIED_URL_LIKE.doParse(jdbcUrl, builder).system(DbSystem.HSQLDB).subtype("https");
      } else {
        builder.subtype("mem").host(null).port(null);
        instance = hsqlUrl;
      }
      return builder.name(instance);
    }
  },

  DERBY("derby") {
    private static final String DEFAULT_USER = "APP";
    private static final int DEFAULT_PORT = 1527;

    @Override
    DBInfo.Builder doParse(String jdbcUrl, DBInfo.Builder builder) {
      String instance = null;
      String host = null;

      DBInfo dbInfo = builder.build();
      if (dbInfo.getUser() == null) {
        builder.user(DEFAULT_USER);
      }

      String derbyUrl = jdbcUrl.substring("derby:".length());
      String[] split = derbyUrl.split(";", 2);

      if (split.length > 1) {
        populateStandardProperties(builder, splitQuery(split[1], ";"));
      }

      String details = split[0];
      if (details.startsWith("memory:")) {
        builder.subtype("memory").host(null).port(null);
        String urlInstance = details.substring("memory:".length());
        if (!urlInstance.isEmpty()) {
          instance = urlInstance;
        }
      } else if (details.startsWith("directory:")) {
        builder.subtype("directory").host(null).port(null);
        String urlInstance = details.substring("directory:".length());
        if (!urlInstance.isEmpty()) {
          instance = urlInstance;
        }
      } else if (details.startsWith("classpath:")) {
        builder.subtype("classpath").host(null).port(null);
        String urlInstance = details.substring("classpath:".length());
        if (!urlInstance.isEmpty()) {
          instance = urlInstance;
        }
      } else if (details.startsWith("jar:")) {
        builder.subtype("jar").host(null).port(null);
        String urlInstance = details.substring("jar:".length());
        if (!urlInstance.isEmpty()) {
          instance = urlInstance;
        }
      } else if (details.startsWith("//")) {
        builder.subtype("network");
        if (dbInfo.getPort() == null) {
          builder.port(DEFAULT_PORT);
        }
        String url = details.substring("//".length());
        int instanceLoc = url.indexOf("/");
        if (instanceLoc >= 0) {
          instance = url.substring(instanceLoc + 1);
          int protoLoc = instance.indexOf(":");
          if (protoLoc >= 0) {
            instance = instance.substring(protoLoc + 1);
          }
          url = url.substring(0, instanceLoc);
        }
        int portLoc = url.indexOf(":");
        if (portLoc > 0) {
          host = url.substring(0, portLoc);
          builder.port(Integer.parseInt(url.substring(portLoc + 1)));
        } else {
          host = url;
        }
      } else {
        builder.subtype("directory").host(null).port(null);
        String urlInstance = details;
        if (!urlInstance.isEmpty()) {
          instance = urlInstance;
        }
      }

      if (host != null) {
        builder.host(host);
      }
      return builder.name(instance);
    }
  };

  private static final Logger log = LoggerFactory.getLogger(JDBCConnectionUrlParser.class);

  private static final Map<String, JDBCConnectionUrlParser> typeParsers = new HashMap<>();

  static {
    for (JDBCConnectionUrlParser parser : JDBCConnectionUrlParser.values()) {
      for (String key : parser.typeKeys) {
        typeParsers.put(key, parser);
      }
    }
  }

  private final String[] typeKeys;

  JDBCConnectionUrlParser(String... typeKeys) {
    this.typeKeys = typeKeys;
  }

  abstract DBInfo.Builder doParse(String jdbcUrl, DBInfo.Builder builder);

  public static DBInfo parse(String connectionUrl, Properties props) {
    if (connectionUrl == null) {
      return DEFAULT;
    }
    // Make this easier and ignore case.
    connectionUrl = connectionUrl.toLowerCase();

    if (!connectionUrl.startsWith("jdbc:")) {
      return DEFAULT;
    }

    String jdbcUrl = connectionUrl.substring("jdbc:".length());
    int typeLoc = jdbcUrl.indexOf(':');

    if (typeLoc < 1) {
      // Invalid format: `jdbc:` or `jdbc::`
      return DEFAULT;
    }

    String type = jdbcUrl.substring(0, typeLoc);
    String system = toDbSystem(type);
    DBInfo.Builder parsedProps = DEFAULT.toBuilder().system(system);
    populateStandardProperties(parsedProps, props);

    try {
      if (typeParsers.containsKey(type)) {
        // Delegate to specific parser
        return withUrl(typeParsers.get(type).doParse(jdbcUrl, parsedProps), type);
      }
      return withUrl(GENERIC_URL_LIKE.doParse(jdbcUrl, parsedProps), type);
    } catch (Exception e) {
      log.debug("Error parsing URL", e);
      return parsedProps.build();
    }
  }

  private static DBInfo withUrl(DBInfo.Builder builder, String type) {
    DBInfo info = builder.build();
    StringBuilder url = new StringBuilder();
    url.append(type);
    url.append(':');
    String subtype = info.getSubtype();
    if (subtype != null) {
      url.append(subtype);
      url.append(':');
    }
    String host = info.getHost();
    if (host != null) {
      url.append("//");
      url.append(host);
      Integer port = info.getPort();
      if (port != null) {
        url.append(':');
        url.append(port);
      }
    }
    return builder.shortUrl(url.toString()).build();
  }

  // Source: https://stackoverflow.com/a/13592567
  private static Map<String, String> splitQuery(String query, String separator) {
    if (query == null || query.isEmpty()) {
      return Collections.emptyMap();
    }
    Map<String, String> query_pairs = new LinkedHashMap<>();
    String[] pairs = query.split(separator);
    for (String pair : pairs) {
      try {
        int idx = pair.indexOf("=");
        String key = idx > 0 ? URLDecoder.decode(pair.substring(0, idx), "UTF-8") : pair;
        if (!query_pairs.containsKey(key)) {
          String value =
              idx > 0 && pair.length() > idx + 1
                  ? URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                  : null;
          query_pairs.put(key, value);
        }
      } catch (UnsupportedEncodingException e) {
        // Ignore.
      }
    }
    return query_pairs;
  }

  private static void populateStandardProperties(
      DBInfo.Builder builder, Map<? extends Object, ? extends Object> props) {
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
        String portNumber = (String) props.get("portnumber");
        try {
          builder.port(Integer.parseInt(portNumber));
        } catch (NumberFormatException e) {
          log.debug("Error parsing portnumber property: " + portNumber, e);
        }
      }

      if (props.containsKey("portNumber")) {
        String portNumber = (String) props.get("portNumber");
        try {
          builder.port(Integer.parseInt(portNumber));
        } catch (NumberFormatException e) {
          log.debug("Error parsing portNumber property: " + portNumber, e);
        }
      }
    }
  }

  /**
   * see {@link <a
   * href="https://github.com/open-telemetry/opentelemetry-specification/blob/master/specification/trace/semantic_conventions/database.md">specification</a>}
   */
  private static String toDbSystem(String type) {
    switch (type) {
      case "as400": // IBM AS400 Database
      case "db2": // IBM Db2
        return DbSystem.DB2;
      case "derby": // Apache Derby
        return DbSystem.DERBY;
      case "h2": // H2 Database
        return DbSystem.H2;
      case "hsqldb": // Hyper SQL Database
        return DbSystem.HSQLDB;
      case "mariadb": // MariaDB
        return DbSystem.MARIADB;
      case "mysql": // MySQL
        return DbSystem.MYSQL;
      case "oracle": // Oracle Database
        return DbSystem.ORACLE;
      case "postgresql": // PostgreSQL
        return DbSystem.POSTGRESQL;
      case "jtds": // jTDS - the pure Java JDBC 3.0 driver for Microsoft SQL Server
      case "microsoft":
      case "sqlserver": // Microsoft SQL Server
        return DbSystem.MSSQL;
      default:
        return DbSystem.OTHER_SQL; // Unknown DBMS
    }
  }
}
