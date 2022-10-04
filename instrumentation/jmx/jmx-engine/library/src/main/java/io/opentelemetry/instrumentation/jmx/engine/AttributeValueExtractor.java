/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.engine;

import static java.util.logging.Level.FINE;
import static java.util.logging.Level.INFO;

import java.util.logging.Level;
import java.util.logging.Logger;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularData;

/**
 * A class responsible for extracting attribute values from MBeans. Objects of this class are
 * immutable.
 */
public class AttributeValueExtractor implements LabelExtractor {

  private static final Logger logger = Logger.getLogger(AttributeValueExtractor.class.getName());

  // The attribute name to be used during value extraction from MBean
  private final String baseName;

  // In case when the extracted attribute is a CompositeData value,
  // how to proceed to arrive at a usable elementary value
  private final String[] nameChain;

  /**
   * Verify the attribute name and create a corresponding extractor object.
   *
   * @param rawName the attribute name, can be a reference to composite values
   * @return the corresponding LabelValueExtractor
   * @throws IllegalArgumentException if the attribute name is malformed
   */
  public static AttributeValueExtractor fromName(String rawName) {
    if (rawName.isEmpty()) {
      throw new IllegalArgumentException("Empty attribute name");
    }

    // Check if a CompositeType value is expected
    int k = rawName.indexOf('.');
    if (k < 0) {
      return new AttributeValueExtractor(rawName);
    }

    // Set up extraction from CompositeType values
    String baseName = rawName.substring(0, k).trim();
    String[] components = rawName.substring(k + 1).split("\\.");

    // sanity check
    if (baseName.isEmpty()) {
      throw new IllegalArgumentException("Invalid attribute name '" + rawName + "'");
    }
    for (int j = 0; j < components.length; ++j) {
      components[j] = components[j].trim();
      if (components[j].isEmpty()) {
        throw new IllegalArgumentException("Invalid attribute name '" + rawName + "'");
      }
    }
    return new AttributeValueExtractor(baseName, components);
  }

  public AttributeValueExtractor(String baseName, String... nameChain) {
    if (baseName == null || nameChain == null) {
      throw new IllegalArgumentException("null argument for AttributeValueExtractor");
    }
    this.baseName = baseName;
    this.nameChain = nameChain;
  }

  /** Get a human readable name of the attribute to extract. Useful for logging or debugging. */
  String getAttributeName() {
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
   * @param server the MBeanServer that reported knowledge of the ObjectName
   * @param objectName the ObjectName identifying the MBean
   * @return AttributeInfo if the attribute is properly recognized, or null
   */
  AttributeInfo getAttributeInfo(MBeanServer server, ObjectName objectName) {
    if (logger.isLoggable(FINE)) {
      logger.log(FINE, "Resolving {0} for {1}", new Object[] {getAttributeName(), objectName});
    }

    try {
      MBeanInfo info = server.getMBeanInfo(objectName);
      MBeanAttributeInfo[] allAttributes = info.getAttributes();

      for (MBeanAttributeInfo attr : allAttributes) {
        if (baseName.equals(attr.getName())) {
          String description = attr.getDescription();

          // Verify correctness of configuration by attempting to extract the metric value.
          // The value will be discarded, but its type will be checked.
          Object sampleValue = extractAttributeValue(server, objectName);

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

  /**
   * Extracts the specified attribute value. In case the value is a CompositeData, drills down into
   * it to find the correct singleton value (usually a Number or a String).
   *
   * @param server the MBeanServer to use
   * @param objectName the ObjectName specifying the MBean to use, it should not be a pattern
   * @param logger the logger to use, may be null
   * @return the attribute value, if found, or null if an error occurred
   */
  private Object extractAttributeValue(MBeanServer server, ObjectName objectName, Logger logger) {
    try {
      Object value = server.getAttribute(objectName, baseName);

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

  private Object extractAttributeValue(MBeanServer server, ObjectName objectName) {
    return extractAttributeValue(server, objectName, null);
  }

  Number extractNumericalAttribute(MBeanServer server, ObjectName objectName) {
    Object value = extractAttributeValue(server, objectName);
    if (value instanceof Number) {
      return (Number) value;
    }
    return null;
  }

  @Override
  public String extractValue(MBeanServer server, ObjectName objectName) {
    return extractStringAttribute(server, objectName);
  }

  private String extractStringAttribute(MBeanServer server, ObjectName objectName) {
    Object value = extractAttributeValue(server, objectName);
    if (value instanceof String) {
      return (String) value;
    }
    return null;
  }
}
