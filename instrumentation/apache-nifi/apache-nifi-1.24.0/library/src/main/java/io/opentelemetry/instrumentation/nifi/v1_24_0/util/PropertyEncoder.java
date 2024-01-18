//package io.opentelemetry.instrumentation.nifi.v1_24_0.util;
//
//import java.nio.charset.StandardCharsets;
//import java.util.AbstractMap.SimpleEntry;
//import java.util.Arrays;
//import java.util.Base64;
//import java.util.HashMap;
//import java.util.Map;
//import java.util.Map.Entry;
//import java.util.stream.Collectors;
//
//import org.apache.commons.lang3.StringUtils;
//import org.apache.nifi.flowfile.FlowFile;
//
//public class PropertyEncoder {
//
//  public static final String NIFI_AGENT_TRACKING_KEY = "nifi.agent.tracking";
//
//  private PropertyEncoder() {}
//
//  public static Map<String, String> decryptToMap(String value) {
//    return insertToMap(new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8));
//  }
//
//  public static Map<String, String> toMap(FlowFile flowFile) {
//
//    String value = flowFile.getAttribute(NIFI_AGENT_TRACKING_KEY);
//
//    if(StringUtils.isBlank(flowFile.getAttribute(NIFI_AGENT_TRACKING_KEY))) {
//      return new HashMap<>();
//    }
//
//    return insertToMap(new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8));
//  }
//
//  public static String fromMap(Map<String, String> map) {
//    return Base64.getEncoder().withoutPadding().encodeToString(extractFromMap(map).getBytes(java.nio.charset.StandardCharsets.UTF_8));
//  }
//
//  private static String extractFromMap(Map<String, String> map) {
//
//    return map.entrySet().stream()
//    .map(e -> e.getKey() + "=" + e.getValue())
//    .collect(Collectors.joining(","));
//  }
//
//  private static Map<String, String> insertToMap(String value)  {
//
//    return Arrays.stream(value.split(","))
//    .map(splits -> splits.split("="))
//    .map(token -> new SimpleEntry<String, String>(token[0], token[1]))
//    .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
//  }
//}