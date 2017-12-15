package dd.inst.jms.util;

import io.opentracing.propagation.TextMap;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import javax.jms.JMSException;
import javax.jms.Message;

public class MessagePropertyTextMap implements TextMap {
  static final String DASH = "_$dash$_";

  private final Message message;

  public MessagePropertyTextMap(final Message message) {
    this.message = message;
  }

  @Override
  public Iterator<Map.Entry<String, String>> iterator() {
    final Map<String, String> map = new HashMap<>();
    try {
      final Enumeration<?> enumeration = message.getPropertyNames();
      if (enumeration != null) {
        while (enumeration.hasMoreElements()) {
          final String key = (String) enumeration.nextElement();
          final Object value = message.getObjectProperty(key);
          if (value instanceof String) {
            map.put(key.replace(DASH, "-"), (String) value);
          }
        }
      }
    } catch (final JMSException e) {
      throw new RuntimeException(e);
    }
    return map.entrySet().iterator();
  }

  @Override
  public void put(final String key, final String value) {
    try {
      message.setStringProperty(key.replace("-", DASH), value);
    } catch (final JMSException e) {
      throw new RuntimeException(e);
    }
  }
}
