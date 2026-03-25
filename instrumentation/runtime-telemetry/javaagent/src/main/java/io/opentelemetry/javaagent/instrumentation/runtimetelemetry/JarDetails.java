/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.runtimetelemetry;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toMap;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/**
 * For a given URL representing a Jar directly on the file system or embedded within another
 * archive, this class provides methods which expose useful information about it.
 */
class JarDetails {
  static final String JAR_EXTENSION = "jar";
  static final String WAR_EXTENSION = "war";
  static final String EAR_EXTENSION = "ear";
  private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();
  private static final Map<String, String> EMBEDDED_FORMAT_TO_EXTENSION =
      Stream.of(JAR_EXTENSION, WAR_EXTENSION, EAR_EXTENSION)
          .collect(
              collectingAndThen(
                  toMap(ext -> ('.' + ext + "!/"), identity()), Collections::unmodifiableMap));
  private static final ThreadLocal<MessageDigest> SHA1 =
      ThreadLocal.withInitial(
          () -> {
            try {
              return MessageDigest.getInstance("SHA1");
            } catch (NoSuchAlgorithmException e) {
              throw new IllegalStateException(e);
            }
          });

  private final URL url;
  @Nullable private final Properties pom;
  @Nullable private final Manifest manifest;
  private final String sha1Checksum;

  private JarDetails(URL url, JarFile jarFile, @Nullable JarEntry jarEntry) throws IOException {
    this.url = url;
    this.pom = getPom(jarFile, jarEntry);
    this.manifest = getManifest(jarFile, jarEntry);
    this.sha1Checksum = computeDigest(jarFile, jarEntry, SHA1.get());
  }

  static JarDetails forUrl(URL url) throws IOException {
    if (url.getProtocol().equals("jar")) {
      String urlString = url.toExternalForm();
      String urlLower = urlString.toLowerCase(Locale.ROOT);
      for (Map.Entry<String, String> entry : EMBEDDED_FORMAT_TO_EXTENSION.entrySet()) {
        int index = urlLower.indexOf(entry.getKey());
        if (index > 0) {
          String targetEntry = urlString.substring(index + entry.getKey().length());
          try (JarFile jarFile =
              new JarFile(
                  urlString.substring(
                      "jar:file:".length(), index + 1 + entry.getValue().length()))) {
            JarEntry jarEntry = jarFile.getJarEntry(targetEntry);
            if (jarEntry == null) {
              throw new IOException("Unable to find nested archive entry: " + targetEntry);
            }
            return new JarDetails(url, jarFile, jarEntry);
          }
        }
      }
    }
    try (JarFile jarFile = new JarFile(url.getFile())) {
      return new JarDetails(url, jarFile, null);
    }
  }

  /**
   * Returns the archive file name, e.g. {@code jackson-datatype-jsr310-2.15.2.jar}. Returns null if
   * unable to identify the file name from {@link #url}.
   */
  @Nullable
  String packagePath() {
    String path = url.getFile();
    int start = path.lastIndexOf('/');
    if (start > -1) {
      return path.substring(start + 1);
    }
    return null;
  }

  /**
   * Returns the extension of the archive, e.g. {@code jar}. Returns null if unable to identify the
   * extension from {@link #url}
   */
  @Nullable
  String packageType() {
    String path = url.getFile();
    int extensionStart = path.lastIndexOf(".");
    if (extensionStart > -1) {
      return path.substring(extensionStart + 1);
    }
    return null;
  }

  /**
   * Returns the maven package name in the format {@code groupId:artifactId}, e.g. {@code
   * com.fasterxml.jackson.datatype:jackson-datatype-jsr310}. Returns null if {@link #pom} is not
   * found, or is missing groupId or artifactId properties.
   */
  @Nullable
  String packageName() {
    if (pom == null) {
      return null;
    }
    String groupId = pom.getProperty("groupId");
    String artifactId = pom.getProperty("artifactId");
    if (groupId != null && !groupId.isEmpty() && artifactId != null && !artifactId.isEmpty()) {
      return groupId + ":" + artifactId;
    }
    return null;
  }

  /**
   * Returns the version from the pom file, e.g. {@code 2.15.2}. Returns null if {@link #pom} is not
   * found or is missing version property.
   */
  @Nullable
  String version() {
    if (pom == null) {
      return null;
    }
    String version = pom.getProperty("version");
    if (version != null && !version.isEmpty()) {
      return version;
    }
    return null;
  }

  /**
   * Returns the package description from the jar manifest "{Implementation-Title} by
   * {Implementation-Vendor}", e.g. {@code Jackson datatype: JSR310 by FasterXML}. Returns null if
   * {@link #manifest} is not found.
   */
  @Nullable
  String packageDescription() {
    if (manifest == null) {
      return null;
    }

    Attributes mainAttributes = manifest.getMainAttributes();
    String name = mainAttributes.getValue(Attributes.Name.IMPLEMENTATION_TITLE);
    if (name == null || name.isEmpty()) {
      return null;
    }
    String description = mainAttributes.getValue(Attributes.Name.IMPLEMENTATION_VENDOR);

    String packageDescription = name;
    if (description != null && !description.isEmpty()) {
      packageDescription += " by " + description;
    }
    return packageDescription;
  }

  /** Returns the SHA1 hash of this file, e.g. {@code 30d16ec2aef6d8094c5e2dce1d95034ca8b6cb42}. */
  String computeSha1() {
    return sha1Checksum;
  }

  private String computeDigest(JarFile jarFile, @Nullable JarEntry jarEntry, MessageDigest md)
      throws IOException {
    md.reset();
    try (InputStream inputStream = getInputStream(jarFile, jarEntry);
        DigestInputStream dis = new DigestInputStream(inputStream, md)) {
      byte[] buffer = new byte[8192];
      while (dis.read(buffer) != -1) {}
      return toHexString(md.digest());
    }
  }

  private static String toHexString(byte[] bytes) {
    char[] chars = new char[bytes.length * 2];
    for (int i = 0; i < bytes.length; i++) {
      int v = bytes[i] & 0xFF;
      chars[i * 2] = HEX_DIGITS[v >>> 4];
      chars[i * 2 + 1] = HEX_DIGITS[v & 0x0F];
    }
    return new String(chars);
  }

  /**
   * Returns An open input stream for the associated url. It is the caller's responsibility to close
   * the stream on completion.
   */
  private InputStream getInputStream(JarFile jarFile, @Nullable JarEntry jarEntry)
      throws IOException {
    if (jarEntry == null) {
      return url.openStream();
    }
    return jarFile.getInputStream(jarEntry);
  }

  @Nullable
  private static Manifest getManifest(JarFile jarFile, @Nullable JarEntry jarEntry) {
    if (jarEntry != null) {
      try (JarInputStream nestedJar = new JarInputStream(jarFile.getInputStream(jarEntry))) {
        return nestedJar.getManifest();
      } catch (IOException e) {
        return null;
      }
    }
    try {
      return jarFile.getManifest();
    } catch (IOException e) {
      return null;
    }
  }

  /**
   * Returns the values from pom.properties if this file is found. If multiple pom.properties files
   * are found or there is an error reading the file, return null.
   */
  @Nullable
  private static Properties getPom(JarFile jarFile, @Nullable JarEntry jarEntry)
      throws IOException {
    if (jarEntry != null) {
      return getEmbeddedPom(jarFile, jarEntry);
    }

    Properties pom = null;
    for (Enumeration<JarEntry> entries = jarFile.entries(); entries.hasMoreElements(); ) {
      JarEntry entry = entries.nextElement();
      if (entry.getName().startsWith("META-INF/maven")
          && entry.getName().endsWith("pom.properties")) {
        if (pom != null) {
          // we've found multiple pom files. bail!
          return null;
        }
        Properties props = new Properties();
        try (InputStream in = jarFile.getInputStream(entry)) {
          props.load(in);
          pom = props;
        }
      }
    }
    return pom;
  }

  @Nullable
  private static Properties getEmbeddedPom(JarFile jarFile, JarEntry jarEntry) throws IOException {
    Properties pom = null;
    // Need to navigate inside the embedded jar which can't be done via random access.
    try (JarInputStream nestedJar = new JarInputStream(jarFile.getInputStream(jarEntry))) {
      for (JarEntry entry = nestedJar.getNextJarEntry();
          entry != null;
          entry = nestedJar.getNextJarEntry()) {
        if (entry.getName().startsWith("META-INF/maven")
            && entry.getName().endsWith("pom.properties")) {
          if (pom != null) {
            // we've found multiple pom files. bail!
            return null;
          }
          Properties props = new Properties();
          props.load(nestedJar);
          pom = props;
        }
      }
    }
    return pom;
  }
}
