//package io.opentelemetry.instrumentation.nifi.v1_24_0.util;
//
//import java.util.Arrays;
//import java.util.Locale;
//
//import io.opentelemetry.instrumentation.nifi.v1_24_0.model.State;
//import org.apache.commons.lang3.StringUtils;
//
//public class AgentLogger {
//
//  public static final String DEMARCATOR = ">>>>";
//
//  private static Boolean enabled = true;
//  private static final int PADDING;
//
//  static {
//
//    PADDING = Arrays.stream(State.values())
//    .map(State::name)
//    .map(String::length)
//    .reduce(Math::max)
//    .get();
//
//    enabled = Boolean.parseBoolean(System.getProperty("nifi.agent.logger.enabled", enabled.toString()));
//  }
//
//  private AgentLogger() {}
//
//  public static synchronized void error(State state, Exception e) {
//
//    if(!enabled) {
//      return;
//    }
//
//    StringBuilder stringBuilder = new StringBuilder();
//
//    stringBuilder.append(DEMARCATOR);
//    stringBuilder.append(StringUtils.SPACE);
//    stringBuilder.append(StringUtils.rightPad(state.name().toUpperCase(Locale.ENGLISH), PADDING));
//    stringBuilder.append(StringUtils.SPACE);
//    stringBuilder.append("Intercept Failure");
//    stringBuilder.append(StringUtils.SPACE);
//    stringBuilder.append(":");
//    stringBuilder.append(StringUtils.SPACE);
//    stringBuilder.append(e.getMessage());
//
//    System.out.println(stringBuilder.toString());
//  }
//
//  public static synchronized void log(State state, String message) {
//
//    if(!enabled) {
//      return;
//    }
//
//    StringBuilder stringBuilder = new StringBuilder();
//
//    stringBuilder.append(DEMARCATOR);
//    stringBuilder.append(StringUtils.SPACE);
//    stringBuilder.append(StringUtils.rightPad(state.name().toUpperCase(Locale.ENGLISH), PADDING));
//    stringBuilder.append(StringUtils.SPACE);
//    stringBuilder.append(":");
//    stringBuilder.append(StringUtils.SPACE);
//    stringBuilder.append(message);
//
//    System.out.println(stringBuilder.toString());
//  }
//}
