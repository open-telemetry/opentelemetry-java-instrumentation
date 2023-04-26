package io.opentelemetry.javaagent.instrumentation.thrift;

import org.apache.thrift.protocol.TField;
import org.apache.thrift.protocol.TMap;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TType;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.apache.thrift.TException;
public class ThriftRequest {
    public TProtocol iport;
    private Map<String, String> attachments = new HashMap<String,String>();

    public Map<String, String> args = new HashMap<String,String>();
    public String methodName;

    public String host;

    public int port;

    public ThriftRequest(TProtocol port){
      this.iport = port;
      this.methodName = "Thrift Call";
      this.port = 100;
      this.host = "0.0.0.0";
    }
    public ThriftRequest setAttachment(String key, String value) {
      if (value == null) {
        this.attachments.remove(key);
      } else {
        this.attachments.put(key, value);
      }
      return this;
    }

    public Map<String, String> getAttachments() {
      return this.attachments;
    }
    public String getAttachment(String key) {
      return this.attachments.get(key);
    }

    public String getMethodName(){
      return this.methodName;
    }

    public void writeAttachment() throws TException {
      short id = 3333;
      TField field = new TField("thriftHeader",TType.MAP,id);
      iport.writeFieldBegin(field);
      try{
        for(String key : this.attachments.keySet()){
          String value = this.attachments.get(key);
          Map<String, String> traceInfo = this.attachments;
          iport.writeMapBegin(new TMap(TType.STRING, TType.STRING, traceInfo.size()));
          for (Map.Entry<String, String> entry : traceInfo.entrySet()) {
            iport.writeString(entry.getKey());
            iport.writeString(entry.getValue());
          }
          iport.writeMapEnd();
        }
      }finally {
        iport.writeFieldEnd();
      }
    }

    public void addArgs(String key, String value){
      this.args.put(key, value);
    }

    public Boolean readAttatchment() throws TException {
      short id = 3333;
        while(true){
          TField schemeField = iport.readFieldBegin();
          if (schemeField.id == 0 && schemeField.type == TType.MAP) {
            TMap _map = iport.readMapBegin();
            this.attachments = new HashMap<String,String>(2 * _map.size);
            for (int i = 0; i < _map.size; ++i) {
              String key = iport.readString();
              String value = iport.readString();
              attachments.put(key, value);
            }
            iport.readMapEnd();
            iport.readFieldEnd();
            break;
          }else if(schemeField.type == TType.STOP){
            iport.readFieldEnd();
            break;
          }
          iport.readFieldEnd();
        }
      return attachments.size() > 0;
    }
    private static final Charset HEADER_CHARSET_ENCODING = StandardCharsets.UTF_8;
    private static ByteBuffer stringToByteBuffer(String s) {
      return ByteBuffer.wrap(s.getBytes(HEADER_CHARSET_ENCODING));
    }

    private static String byteBufferToString(ByteBuffer buf) {
      CharBuffer charBuffer = HEADER_CHARSET_ENCODING.decode(buf);
      return charBuffer.toString();
    }


}
