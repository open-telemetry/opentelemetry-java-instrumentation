//package io.opentelemetry.instrumentation.nifi.v1_24_0.util;
//
//import java.util.ArrayList;
//import java.util.Arrays;
//import java.util.Collection;
//import java.util.HashMap;
//import java.util.HashSet;
//import java.util.List;
//import java.util.Map;
//import java.util.Map.Entry;
//import java.util.Set;
//import java.util.TreeMap;
//import java.util.function.BiFunction;
//import java.util.function.Consumer;
//import java.util.function.Predicate;
//import java.util.Locale;
//
//import io.opentelemetry.instrumentation.nifi.v1_24_0.model.Instrumentation;
//import org.apache.commons.lang3.StringUtils;
//import org.apache.commons.lang3.reflect.FieldUtils;
//import org.apache.nifi.components.PropertyDescriptor;
//import org.apache.nifi.processor.ProcessContext;
//import org.apache.nifi.processor.Processor;
//import org.apache.nifi.processor.StandardProcessContext;
//
///**
// * This class provides the ability to dynamically modify @Processor configuration properties
// */
//public class PropertyMutator {
//
//  public static List<String> propertyExclusions = new ArrayList<>();
//  public static Predicate<Entry<String, Object>> nonExclusion = e -> !PropertyMutator.propertyExclusions.contains(e.getKey());
//
//  private static final Map<String, Modification> modificationMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
//
//  private PropertyMutator() {}
//
//  public static final BiFunction<String, Set<String>, String> concatRegexOrExpressions = (regex, attributes) -> {
//
//    if(StringUtils.isBlank(regex)) {
//      return regexOrExpressionsCombiner(attributes);
//    }
//
//    String expression = regexOrExpressionsCombiner(attributes);
//
//    if(regex.contains(expression)) {
//      return regex;
//    }
//
//    attributes.add(regex);
//
//    return regexOrExpressionsCombiner(attributes);
//  };
//
//  static {
//
//    propertyExclusions.add("contentclaim");
//    propertyExclusions.add("contentclaimoffset");
//    propertyExclusions.add("properties");
//    propertyExclusions.add("attributes");
//
//    Modification postHttpMod = new Modification("PostHttp", "Http Headers", Instrumentation.TRACE_PARENT);
//    postHttpMod.setRegexFunction(concatRegexOrExpressions);
//
//    Modification invokeHttpMod = new Modification("InvokeHttp", "Attributes to Send", Instrumentation.TRACE_PARENT);
//    invokeHttpMod.setRegexFunction(concatRegexOrExpressions);
//
//    modificationMap.put(invokeHttpMod.getProcessorName(), invokeHttpMod);
//    modificationMap.put(postHttpMod.getProcessorName(), postHttpMod);
//  }
//
//  public static void registerModification(Modification modification) {
//    modificationMap.put(modification.getProcessorName(), modification);
//  }
//
//  private static String regexOrExpressionsCombiner(Collection<String> regexList) {
//    return String.join("|", regexList);
//  }
//
//  public static void mutate(ProcessContext processContext, Processor processor, Consumer<Modification> modifier) {
//
//    String processorName = processor.getClass().getSimpleName();
//
//    if(!modificationMap.containsKey(processorName)) {
//      return;
//    }
//
//  Modification modification = modificationMap.get(processorName).with(processContext);
//
//  modifier.accept(modification);
//}
//
//private static Pair<PropertyDescriptor, String> findPropertyDescriptor(Map<PropertyDescriptor, String> properties, String matcher) throws Exception {
//
//  Entry<PropertyDescriptor, String> entry = properties.entrySet().stream()
//  .filter(e -> e.getKey().getName().toLowerCase(Locale.ENGLISH).contains(matcher.toLowerCase(Locale.ENGLISH)))
//    .findAny()
//    .orElseThrow(() -> new Exception("No PropertyDescriptor found with expression [" + matcher + "]"));
//
//    return new Pair<PropertyDescriptor, String>(entry.getKey(), entry.getValue());
//  }
//
//  public static Pair<String, Map<PropertyDescriptor, String>> findProcessorPropertyMap(StandardProcessContext standardProcessContext) throws Exception {
//
//    String fieldName = Insight.findMapNameByGenerics(StandardProcessContext.class, PropertyDescriptor.class, String.class);
//    return new Pair<>(fieldName, FieldUtils.readField(standardProcessContext, fieldName, true));
//  }
//
//  public static void reInject(StandardProcessContext standardProcessContext, String fieldName, Map<PropertyDescriptor, String> map) throws Exception {
//    FieldUtils.writeField(standardProcessContext, fieldName, map, true);
//  }
//
//  @SuppressWarnings("unchecked")
//  public static class Pair<K, V> {
//
//    private final Object key;
//    private final Object value;
//
//    public Pair(Object key, Object value) {
//
//      this.key = key;
//      this.value = value;
//    }
//
//    public K getKey() {
//      return (K) key;
//    }
//
//    public V getValue() {
//      return (V) value;
//    }
//
//    public String keyToString() throws Exception {
//      return (String) key;
//    }
//
//    public Map<PropertyDescriptor, String> valueToMap(){
//      return new HashMap<>((Map<PropertyDescriptor, String>) value);
//    }
//  }
//
//  public static class Modification {
//
//    public static final BiFunction<String, Set<String>, String> DEFAULT = PropertyMutator.concatRegexOrExpressions;
//
//    private final String processorName;
//    private final String propertyDesriptorMatcher;
//    private final Set<String> attributes = new HashSet<>();
//
//    @org.jetbrains.annotations.Nullable
//    private StandardProcessContext standardProcessContext;
//    private BiFunction<String, Set<String>, String> regexFunction = DEFAULT;
//
//    public Modification(String processorName, String propertyDesriptorMatcher, String...attributes) {
//      this.processorName = processorName;
//      this.propertyDesriptorMatcher = propertyDesriptorMatcher;
//      this.attributes.addAll(Arrays.asList(attributes));
//    }
//
//    public String getPropertyDesriptorMatcher() {
//      return propertyDesriptorMatcher;
//    }
//
//    public String getProcessorName() {
//      return processorName;
//    }
//
//    @org.jetbrains.annotations.Nullable
//    public StandardProcessContext getStandardProcessContext() {
//      return standardProcessContext;
//    }
//
//    public void setRegexFunction(BiFunction<String, Set<String>, String> regexFunction) {
//      this.regexFunction = regexFunction;
//    }
//
//    public BiFunction<String, Set<String>, String> getRegexFunction() {
//      return regexFunction;
//    }
//
//    public String modifyRegex(String existingValue) {
//      return regexFunction.apply(existingValue, attributes);
//    }
//
//    @com.google.errorprone.annotations.CanIgnoreReturnValue
//    public Modification with(ProcessContext processContext) {
//      this.standardProcessContext = (org.apache.nifi.processor.StandardProcessContext) processContext;
//      return this;
//    }
//
//    public void setStandardProcessContext(ProcessContext processContext) {
//      this.standardProcessContext = (org.apache.nifi.processor.StandardProcessContext) processContext;
//    }
//
//    public Set<String> getAttributesToAdd(){
//      return attributes;
//    }
//  }
//
//  public static final Consumer<Modification> DEFAULT_APPROACH = modification -> {
//
//    synchronized (modification) {
//
//      try {
//
//        StandardProcessContext standardProcessContext = modification.getStandardProcessContext();
//
//        if(standardProcessContext == null) {
//
//          throw new Exception("StandardProcessContext is null");
//        }
//
//        Pair<String, Map<PropertyDescriptor, String>> fieldDescriptorPair = findProcessorPropertyMap(standardProcessContext);
//
//        String fieldName = fieldDescriptorPair.getKey();
//        Map<PropertyDescriptor, String> immutableMap = fieldDescriptorPair.getValue();
//
//        String propertyDescriptorMatcher = modification.getPropertyDesriptorMatcher();
//
//        Pair<PropertyDescriptor, String> propertyValuePair = findPropertyDescriptor(immutableMap, propertyDescriptorMatcher);
//
//        PropertyDescriptor propertyDescriptor = propertyValuePair.getKey();
//        String propertyValue = propertyValuePair.getValue();
//
//        String modifiedRegex = modification.modifyRegex(propertyValue);
//
//        Map<PropertyDescriptor, String> deepCopyPropertyMap = new HashMap<>(immutableMap);
//        deepCopyPropertyMap.put(propertyDescriptor, modifiedRegex);
//
//        PropertyMutator.reInject(standardProcessContext, fieldName, deepCopyPropertyMap);
//      }
//      catch(Exception e) {
//
//        System.out.println(">>>> Modifier : Failed to modify [" + modification.getProcessorName() + "] PropertyDescriptor : " + e.getMessage());
//      }
//    }
//  };
//}
