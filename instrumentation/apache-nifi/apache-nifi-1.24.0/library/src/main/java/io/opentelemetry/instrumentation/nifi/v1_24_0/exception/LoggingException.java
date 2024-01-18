package io.opentelemetry.instrumentation.nifi.v1_24_0.exception;

import java.util.Arrays;

public class LoggingException extends Exception {

  private static final long serialVersionUID = 1L;

  public static enum StatusCode {
   
    ACKNOWLEDGEABLE_ERROR(0),
    INSIGNIFICANT_ERROR(1);
    
    private final int code;
    
    StatusCode(int code){
      this.code = code;
    }
    
    public int getCode() {
      return code;
    }
    
    public static StatusCode toStatusCode(int code) throws Exception {
      
      return Arrays.stream(StatusCode.values())
      .filter(s -> s.getCode() == code)
      .findAny().orElseThrow(() -> new Exception("Unknown Code [" + code + "]"));
    }
  }
  
  private StatusCode statusCode = StatusCode.ACKNOWLEDGEABLE_ERROR;
  
  public LoggingException(String errorMessage) {
    super(errorMessage);
  }
  
  public LoggingException(String errorMessage, StatusCode statusCode) {
    super(errorMessage);
    this.statusCode = statusCode;
  }
  
  public StatusCode getStatusCode() {
    return statusCode;
  }
}
