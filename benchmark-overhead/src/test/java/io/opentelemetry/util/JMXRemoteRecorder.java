package io.opentelemetry.util;

import javax.management.JMX;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.TabularDataSupport;
import javax.management.openmbean.TabularType;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import jdk.management.jfr.FlightRecorderMXBean;

/**
 * Utility for starting a JFR recording in a remote VM via RMI/JMX.
 */
public class JMXRemoteRecorder {

  private static final String MAP_STRING_STRING = "java.util.Map<java.lang.String, java.lang.String>";
  private static final String[] KEY_VALUE = new String[] {"key", "value"};

  public static void main(String[] args) throws Exception {
    String host = args[0];
    String port = args[1];
    String jfrName = args[2];
    String jfrSettings = args[3];
    String jfrFile = args[4];

    String urlPath = String.format("/jndi/rmi://%s:%s/jmxrmi", host, port);
    JMXServiceURL url = new JMXServiceURL("rmi", "", 0, urlPath);
    JMXConnector jmxConnector = JMXConnectorFactory.connect(url);
    MBeanServerConnection connection = jmxConnector.getMBeanServerConnection();
    ObjectName objectName = new ObjectName("jdk.management.jfr:type=FlightRecorder");

    FlightRecorderMXBean jfr = JMX.newMBeanProxy(connection, objectName,
        FlightRecorderMXBean.class);

    long recordingId = jfr.newRecording();
    jfr.setPredefinedConfiguration(recordingId, jfrSettings);
    setOptions(jfrName, jfrFile, connection, recordingId);

    jfr.startRecording(recordingId);
    System.out.println("Recording " + jfrName + " started on " + host + ":" + port);
  }

  /**
   * Complex types like Map<String,String> are challenging over JMX, so this method helps
   * build the tabular data required.
   */
  private static void setOptions(String jfrName, String jfrFile, MBeanServerConnection connection, long recordingId)
      throws Exception {
    ObjectName objectName = new ObjectName("jdk.management.jfr:type=FlightRecorder");
    TabularType tabularType = new TabularType(MAP_STRING_STRING, MAP_STRING_STRING,
        compositeType(), new String[] {"key"});
    TabularDataSupport table = new TabularDataSupport(tabularType);
    table.put(composeKV("disk", "true"));
    table.put(composeKV("dumpOnExit", "true"));
    table.put(composeKV("name", jfrName));
    table.put(composeKV("destination", jfrFile));
    String[] sig = new String[] {"long", "javax.management.openmbean.TabularData"};
    Object[] args = new Object[] {recordingId, table};
    connection.invoke(objectName, "setRecordingOptions", args, sig);
  }

  private static CompositeDataSupport composeKV(String key, String value)
      throws OpenDataException {
    return new CompositeDataSupport(compositeType(), KEY_VALUE, new Object[] {key, value});
  }

  private static CompositeType compositeType() throws OpenDataException {
    return new CompositeType(MAP_STRING_STRING, MAP_STRING_STRING, KEY_VALUE,
        KEY_VALUE, new OpenType[] {SimpleType.STRING, SimpleType.STRING});
  }
}
