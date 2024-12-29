/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.engine;

import static java.util.logging.Level.FINE;
import static java.util.logging.Level.INFO;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularData;

/**
 * A class responsible for extracting attribute values from MBeans. Objects of this class are
 * immutable.
 */
public class BeanAttributeExtractor implements MetricAttributeExtractor {

  private static final Logger logger = Logger.getLogger(BeanAttributeExtractor.class.getName());

  // The attribute name to be used during value extraction from MBean
  private final String baseName;

  // In case when the extracted attribute is a CompositeData value,
  // how to proceed to arrive at a usable elementary value
  private final String[] nameChain;

  /**
   * Verify the attribute name and create a corresponding extractor object.
   *
   * @param rawName the attribute name, can be a reference to composite values
   * @return the corresponding BeanAttributeExtractor
   * @throws IllegalArgumentException if the attribute name is malformed
   */
  public static BeanAttributeExtractor fromName(String rawName) {
    List<String> segments = splitByDot(rawName);
    String baseName = segments.remove(0);
    if (segments.isEmpty()) {
      return new BeanAttributeExtractor(baseName);
    }
    return new BeanAttributeExtractor(baseName, segments.toArray(new String[segments.size()]));
  }

  /*
   * Split a given name into segments, assuming that a dot is used to separate the segments.
   * However, a dot preceded by a backslash is not a separator.
   */
  private static List<String> splitByDot(String rawName) {
    List<String> components = new ArrayList<>();
    try {
      StringBuilder currentSegment = new StringBuilder();
      boolean escaped = false;
      for (int i = 0; i < rawName.length(); ++i) {
        char ch = rawName.charAt(i);
        if (escaped) {
          // Allow only '\' and '.' to be escaped
          if (ch != '\\' && ch != '.') {
            throw new IllegalArgumentException(
                "Invalid escape sequence in attribute name '" + rawName + "'");
          }
          currentSegment.append(ch);
          escaped = false;
        } else {
          if (ch == '\\') {
            escaped = true;
          } else if (ch == '.') {
            // this is a segment separator
            verifyAndAddNameSegment(components, currentSegment);
            currentSegment = new StringBuilder();
          } else {
            currentSegment.append(ch);
          }
        }
      }

      // The returned list is never empty ...
      verifyAndAddNameSegment(components, currentSegment);

    } catch (IllegalArgumentException unused) {
      // Drop the original exception. We have more meaningful context here.
      throw new IllegalArgumentException("Invalid attribute name '" + rawName + "'");
    }

    return components;
  }

  private static void verifyAndAddNameSegment(List<String> segments, StringBuilder candidate) {
    String newSegment = candidate.toString().trim();
    if (newSegment.isEmpty()) {
      throw new IllegalArgumentException();
    }
    segments.add(newSegment);
  }

  public BeanAttributeExtractor(String baseName, String... nameChain) {
    if (baseName == null || nameChain == null) {
      throw new IllegalArgumentException("null argument for BeanAttributeExtractor");
    }
    this.baseName = baseName;
    this.nameChain = nameChain;
  }

  /**
   * Get a human readable name of the attribute to extract. Used to form the metric name if none is
   * provided. Also useful for logging or debugging.
   */
  public String getAttributeName() {
    if (nameChain.length > 0) {
      StringBuilder builder = new StringBuilder(baseName);
      for (String component : nameChain) {
        builder.append(".").append(component);
      }
      return builder.toString();
    } else {
      return baseName;
    }
  }

  /**
   * Verify that the MBean identified by the given ObjectName recognizes the configured attribute,
   * including the internals of CompositeData and TabularData, if applicable, and that the provided
   * values will be numerical.
   *
   * @param connection the {@link MBeanServerConnection} that reported knowledge of the ObjectName
   * @param objectName the {@link ObjectName} identifying the MBean
   */
  @Nullable
  AttributeInfo getAttributeInfo(MBeanServerConnection connection, ObjectName objectName) {
    if (logger.isLoggable(FINE)) {
      logger.log(FINE, "Resolving {0} for {1}", new Object[] {getAttributeName(), objectName});
    }

    try {
      MBeanInfo info = connection.getMBeanInfo(objectName);
      MBeanAttributeInfo[] allAttributes = info.getAttributes();

      for (MBeanAttributeInfo attr : allAttributes) {
        if (baseName.equals(attr.getName())) {
          String description = attr.getDescription();

          // Verify correctness of configuration by attempting to extract the metric value.
          // The value will be discarded, but its type will be checked.
          Object sampleValue = getSampleValue(connection, objectName);

          // Only numbers can be used to generate metric values
          if (sampleValue instanceof Number) {
            return new AttributeInfo((Number) sampleValue, description);
          } else {
            // It is fairly normal to get null values, especially during startup,
            // but it is much more suspicious to get non-numbers
            Level logLevel = sampleValue == null ? FINE : INFO;
            if (logger.isLoggable(logLevel)) {
              logger.log(
                  logLevel,
                  "Unusable value {0} for attribute {1} and ObjectName {2}",
                  new Object[] {
                    sampleValue == null ? "NULL" : sampleValue.getClass().getName(),
                    getAttributeName(),
                    objectName
                  });
            }
            return null;
          }
        }
      }

      if (logger.isLoggable(FINE)) {
        logger.log(
            FINE,
            "Cannot find attribute {0} for ObjectName {1}",
            new Object[] {baseName, objectName});
      }

    } catch (InstanceNotFoundException e) {
      // Should not happen. The ObjectName we use has been provided by the MBeanServer we use.
      logger.log(INFO, "The MBeanServer does not find {0}", objectName);
    } catch (Exception e) {
      logger.log(
          FINE,
          "Exception {0} while inspecting attributes for ObjectName {1}",
          new Object[] {e, objectName});
    }
    return null;
  }

  @Nullable
  protected Object getSampleValue(MBeanServerConnection connection, ObjectName objectName) {
    return extractAttributeValue(connection, objectName, logger);
  }

  /**
   * Extracts the specified attribute value. In case the value is a CompositeData, drills down into
   * it to find the correct singleton value (usually a Number or a String).
   *
   * @param connection the {@link MBeanServerConnection} to use
   * @param objectName the {@link ObjectName} specifying the MBean to use, it should not be a
   *     pattern
   * @param logger the logger to use, may be null. Typically we want to log any issues with the
   *     attributes during MBean discovery, but once the attribute is successfully detected and
   *     confirmed to be eligible for metric evaluation, any further attribute extraction
   *     malfunctions will be silent to avoid flooding the log.
   * @return the attribute value, if found, or {@literal null} if an error occurred
   */
  @Nullable
  private Object extractAttributeValue(
      MBeanServerConnection connection, ObjectName objectName, Logger logger) {
    try {
      Object value = connection.getAttribute(objectName, baseName);

      int k = 0;
      while (k < nameChain.length) {
        if (value instanceof CompositeData) {
          value = ((CompositeData) value).get(nameChain[k]);
        } else if (value instanceof TabularData) {
          value = ((TabularData) value).get(new String[] {nameChain[k]});
        } else {
          if (logger != null) {
            logger.log(
                FINE,
                "Encountered a value of {0} while extracting attribute {1} for ObjectName {2}; unable to extract metric value",
                new Object[] {
                  (value == null ? "NULL" : value.getClass().getName()),
                  getAttributeName(),
                  objectName
                });
          }
          break;
        }
        k++;
      }
      return value;
    } catch (Exception e) {
      // We do not really care about the actual reason for failure
      if (logger != null) {
        logger.log(
            FINE,
            "Encountered {0} while extracting attribute {1} for ObjectName {2}; unable to extract metric value",
            new Object[] {e, getAttributeName(), objectName});
      }
    }
    return null;
  }

  @Nullable
  private Object extractAttributeValue(MBeanServerConnection connection, ObjectName objectName) {
    return extractAttributeValue(connection, objectName, null);
  }

  @Nullable
  protected Number extractNumericalAttribute(
      MBeanServerConnection connection, ObjectName objectName) {
    Object value = extractAttributeValue(connection, objectName);
    if (value instanceof Number) {
      return (Number) value;
    }
    return null;
  }

  @Override
  @Nullable
  public String extractValue(MBeanServerConnection connection, ObjectName objectName) {
    return extractStringAttribute(connection, objectName);
  }

  @Nullable
  private String extractStringAttribute(MBeanServerConnection connection, ObjectName objectName) {
    Object value = extractAttributeValue(connection, objectName);
    if (value instanceof String) {
      return (String) value;
    }
    if (value instanceof Boolean) {
      return value.toString();
    }
    if (value instanceof Enum) {
      return ((Enum<?>) value).name();
    }
    return null;
  }
}
