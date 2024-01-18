//package io.opentelemetry.instrumentation.nifi.v1_24_0.util;
//
//import java.lang.reflect.Field;
//import java.lang.reflect.Method;
//import java.lang.reflect.ParameterizedType;
//import java.lang.reflect.Type;
//import java.util.AbstractMap.SimpleEntry;
//import java.util.Map.Entry;
//import java.util.function.Function;
//import java.util.stream.Collectors;
//import java.util.stream.Stream;
//import java.util.Map;
//import java.util.Optional;
//import java.util.Arrays;
//import java.util.Objects;
//import java.util.HashMap;
//import java.util.List;
//import java.util.ArrayList;
//
//import io.opentelemetry.instrumentation.nifi.v1_24_0.model.Identity;
//import io.opentelemetry.instrumentation.nifi.v1_24_0.model.State;
//import io.opentelemetry.instrumentation.nifi.v1_24_0.util.PropertyMutator.Pair;
//import org.apache.commons.io.FileUtils;
//import org.apache.commons.lang3.StringUtils;
//import org.apache.commons.lang3.reflect.FieldUtils;
//import org.apache.nifi.connectable.Connectable;
//import org.apache.nifi.controller.ProcessorNode;
//import org.apache.nifi.controller.repository.RepositoryContext;
//import org.apache.nifi.controller.repository.RepositoryRecordType;
//import org.apache.nifi.controller.repository.StandardProcessSession;
//import org.apache.nifi.controller.repository.StandardProcessSessionFactory;
//import org.apache.nifi.controller.repository.StandardRepositoryContext;
//import org.apache.nifi.controller.repository.StandardRepositoryRecord;
//import org.apache.nifi.flowfile.FlowFile;
//import org.apache.nifi.flowfile.attributes.CoreAttributes;
//import org.apache.nifi.logging.LogObserver;
//import org.apache.nifi.logging.ProcessorLogObserver;
//import org.apache.nifi.processor.ProcessSession;
//import org.apache.nifi.processor.ProcessSessionFactory;
//import org.apache.nifi.processor.StandardProcessContext;
//
//@SuppressWarnings("unchecked")
//public class Insight {
//
//  public static final boolean HUMAN_READABLE_FILE_SIZE = false;
//
//  private static final Map<Insight, String> memoizationMap = new HashMap<>();
//
//  @io.opentelemetry.testing.internal.io.micrometer.common.lang.NonNull
//  private final String target;
//
//  @io.opentelemetry.testing.internal.io.micrometer.common.lang.NonNull
//  private final String lookup;
//
//  @org.jetbrains.annotations.Nullable
//  private String fieldName;
//
//  public Insight(Class<?> target, Class<?> lookup) {
//
//    this.target = Optional.ofNullable(target).map(Class::getName).orElse("unknown");
//    this.lookup = Optional.ofNullable(lookup).map(Class::getName).orElse("unknown");
//  }
//
//  public void setFieldName(@org.jetbrains.annotations.Nullable String fieldName) {
//    this.fieldName = fieldName;
//  }
//
//  public @org.jetbrains.annotations.Nullable String getFieldName() {
//    return fieldName;
//  }
//
//  @Override
//  public int hashCode() {
//    return Objects.hash(lookup, target);
//  }
//
//  @Override
//  @SuppressWarnings("EqualsGetClass")
//  public boolean equals(Object obj) {
//    if (this == obj) {
//      return true;
//    }
//    if (obj == null) {
//      return false;
//    }
//    if (!getClass().equals(obj.getClass())) {
//      return false;
//    }
//
//    Insight other = (Insight) obj;
//
//    return Objects.equals(lookup, other.lookup) && Objects.equals(target, other.target);
//  }
//
//  @Override
//  public String toString() {
//
//    StringBuilder builder = new StringBuilder();
//
//    builder.append("Insight [targetObj=").append(target)
//    .append(", lookupObj=").append(lookup)
//    .append(", fieldName=").append(fieldName).append("]");
//
//    return builder.toString();
//  }
//
//  private static enum Match {NAME, ASSIGNABLE, EITHER, BOTH}
//
//  private static boolean isMatch(Class<?> a, Class<?> b, Match match) {
//
//    if(Match.NAME == match) {
//     return a.getSimpleName().equals(b.getSimpleName());
//    }
//
//    if(Match.BOTH == match) {
//      return a.getSimpleName().equals(b.getSimpleName()) && a.isAssignableFrom(b);
//    }
//
//    if(Match.ASSIGNABLE == match) {
//      return a.isAssignableFrom(b);
//    }
//
//    return a.getSimpleName().equals(b.getSimpleName()) || a.isAssignableFrom(b);
//  }
//
//  public static String findFieldName(Class<?> target, Object object) throws Exception {
//
//    Insight insight = new Insight(target, object.getClass());
//
//    if(memoizationMap.containsKey(insight)) {
//      return memoizationMap.get(insight);
//    }
//
//    String targetType = target.getSimpleName();
//
//    Field field = Arrays.stream(object.getClass().getDeclaredFields())
//            .filter(f -> isMatch(f.getType(), target, Match.ASSIGNABLE))
//            .findAny()
//            .orElseThrow(() -> new Exception("Insight : Field not found [" + targetType + "]"));
//
//    insight.setFieldName(field.getName());
//
//    memoizationMap.put(insight, field.getName());
//
//    return field.getName();
//  }
//
//  @org.jetbrains.annotations.Nullable
//  public static String findFieldName(Class<?> target, Class<?> clazz) throws Exception {
//
//    Insight insight = new Insight(target, clazz);
//
//    if(memoizationMap.containsKey(insight)) {
//      return memoizationMap.get(insight);
//    }
//
//    Optional<Field> field = Arrays.stream(clazz.getDeclaredFields())
//    .filter(f -> isMatch(f.getType(), target, Match.EITHER))
//    .findFirst();
//
//    if(field.isEmpty()) {
//      throw new Exception("Insight : Field not found [" + target.getName() + "]");
//    }
//
//    String fieldName = field.get().getName();
//
//    insight.setFieldName(fieldName);
//
//    memoizationMap.put(insight, fieldName);
//
//    return insight.getFieldName();
//  }
//
//  public static String findMapNameByGenerics(Class<?> clazz, Class<?> genericMapKey, Class<?> genericMapValue) throws Exception {
//
//    Insight insight = new Insight(Map.class, clazz);
//
//    if(memoizationMap.containsKey(insight)) {
//      return memoizationMap.get(insight);
//    }
//
//    String fieldName = Arrays.stream(clazz.getDeclaredFields())
//    .filter(f -> isMatch(f.getType(), Map.class, Match.ASSIGNABLE))
//    .map(f -> new Pair<String, ParameterizedType>(f.getName(), (ParameterizedType) f.getGenericType()))
//    .map(p -> new Pair<String, Type[]>(p.getKey(), p.getValue().getActualTypeArguments()))
//    .map(p -> new Pair<String, Pair<String, String>>(p.getKey(), new Pair<>(p.getValue()[0].getTypeName(), p.getValue()[1].getTypeName())))
//    .filter(p -> p.getValue().getKey().equalsIgnoreCase(genericMapKey.getTypeName()))
//    .filter(p -> p.getValue().getValue().equalsIgnoreCase(genericMapValue.getTypeName()))
//    .map(Pair::getKey)
//    .findFirst()
//    .orElseThrow(() -> new Exception("Insight : Map not found : Map<" + genericMapKey.getSimpleName() + "," + genericMapValue.getSimpleName() + ">"));
//
//    insight.setFieldName(fieldName);
//
//    memoizationMap.put(insight, fieldName);
//
//    return fieldName;
//  }
//
//  public static Optional<String> getParentFlowFileUuid(ProcessSession processSession) throws Exception {
//
//    return Insight.getRecordMap(Insight.autoCast(processSession)).values().stream()
//    .filter(r -> r.getType() == RepositoryRecordType.CREATE)
//    .map(StandardRepositoryRecord::getCurrent)
//    .map(f -> f.getAttribute(CoreAttributes.UUID.key()))
//    .findFirst();
//  }
//
//  @org.jetbrains.annotations.Nullable
//  public static StandardRepositoryRecord getStandardRepositoryRecord(StandardProcessSession standardProcessSession, FlowFile flowFile) throws Exception {
//    return getRecordMap(standardProcessSession).get(flowFile.getId());
//  }
//
//  public static List<StandardRepositoryRecord> getStandardRepositoryRecord(ProcessSession processSession) throws Exception {
//    return new ArrayList<>(getRecordMap(Insight.autoCast(processSession)).values());
//  }
//
//  public static Optional<StandardRepositoryRecord> getStandardRepositoryRecord(ProcessSession processSession, RepositoryRecordType repositoryRecordType) throws Exception {
//
//    return Insight.getRecordMap(Insight.autoCast(processSession)).values().stream()
//    .filter(r -> r.getType() == repositoryRecordType)
//    .findFirst();
//  }
//
//  public static Optional<StandardRepositoryRecord> getStandardRepositoryRecordParent(ProcessSession processSession) throws Exception {
//
//    return Insight.getRecordMap(Insight.autoCast(processSession)).values().stream()
//    .filter(r -> r.getType() == RepositoryRecordType.CREATE)
//    .findFirst();
//  }
//
//  public static boolean isGetter(Method method){
//
//    return Optional.of(method)
//    .filter(m -> m.getParameterCount() == 0)
//    .filter(m -> !m.getReturnType().equals(void.class))
//    .map(Method::getName)
//    .filter(name -> StringUtils.startsWithAny(name, "is", "get"))
//    .isPresent();
//  }
//
//  public static Function<String, String> customKeyFormatter = input -> {
//
//    if(input.equals("size")) {
//      return "filesize.bytes";
//    }
//    else {
//      return input;
//    }
//  };
//
//  public static Function<SimpleEntry<String, Object>, SimpleEntry<String, Object>> customValueFormatter = entry -> {
//
//    if(HUMAN_READABLE_FILE_SIZE) {
//
//      if(StringUtils.startsWithAny(entry.getKey(), "size", "filesize")) {
//
//        Long value = (long) entry.getValue();
//        String normalized = FileUtils.byteCountToDisplaySize(value);
//
//        entry.setValue(normalized);
//      }
//    }
//
//    return entry;
//  };
//
//  public static Function<String, String> trimNamingConvention = input -> {
//
//    if(input.startsWith("get")) {
//      return StringUtils.removeStart(input, "get");
//    }
//    else if(input.startsWith("is")) {
//      return StringUtils.removeStart(input, "is");
//    }
//    else {
//      return input;
//    }
//  };
//
//  public static String normalizeNameForAttribute(Method method) {
//
//    return Stream.of(method.getName())
//    .map(trimNamingConvention)
//    .map(String::toLowerCase)
//    .map(customKeyFormatter)
//    .collect(Collectors.joining());
//  }
//
//  @org.jetbrains.annotations.Nullable
//  private static SimpleEntry<String, Object> evaluateToEntry (Method method, Object object) {
//
//    SimpleEntry<String, Object> entry = null;
//
//    try {
//      entry = new SimpleEntry<String, Object>(normalizeNameForAttribute(method), method.invoke(object));
//    }
//    catch(Exception e) {
//      AgentLogger.error(State.NULL, e);
//    }
//
//    return entry;
//  };
//
//  public static Map<String, Object> getAllGetterMethods(Object object){
//
//    return Arrays.stream(object.getClass().getDeclaredMethods())
//    .filter(Insight::isGetter)
//    .map(method -> evaluateToEntry(method, object))
//    .filter(Objects::nonNull)
//    .map(customValueFormatter)
//    .filter(PropertyMutator.nonExclusion)
//    .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
//  }
//
//  public static Method findMethod(Class<?> target, String methodName, Class<?> returnType) throws Exception {
//
//    Method method =  Arrays.stream(target.getDeclaredMethods())
//    .filter(m -> m.getName().equals(methodName) || m.getName().contains(methodName) )
//    .filter(m -> m.getReturnType().getTypeName().equals(returnType.getTypeName()))
//    .findAny()
//    .orElseThrow(() -> new NoSuchMethodException("Method not found [" + methodName + "]"));
//
//    method.setAccessible(true);
//
//    return method;
//  }
//
//  public static Long getProcessingStartTime(StandardProcessSession standardProcessSession) throws Exception {
//    return autoCast(FieldUtils.readField(standardProcessSession, "processingStartTime", true));
//  }
//
//  public static Long getSessionId(StandardProcessSession standardProcessSession) throws Exception {
//    return autoCast(FieldUtils.readField(standardProcessSession, "sessionId", true));
//  }
//
//  public static Map<Long, StandardRepositoryRecord> getRecordMap(StandardProcessSession standardProcessSession) throws Exception {
//
//    String fieldName = Insight.findMapNameByGenerics(StandardProcessSession.class, Long.class, StandardRepositoryRecord.class);
//    return autoCast(FieldUtils.readField(standardProcessSession, fieldName, true));
//  }
//
//  public static Optional<StandardRepositoryRecord> getFirstStandardRepositoryRecord(ProcessSession processSession) throws Exception {
//    return Insight.getRecordMap(Insight.autoCast(processSession)).values().stream().filter(Objects::nonNull).findFirst();
//  }
//
//  public static Connectable getConnectable(StandardProcessSession standardProcessSession) throws Exception {
//    return getRepositoryContext(standardProcessSession).getConnectable();
//  }
//
//  public static ProcessorNode getProcessorNode(ProcessorLogObserver processorLogObserver) throws Exception {
//
//    String fieldName = findFieldName(ProcessorNode.class, ProcessorLogObserver.class);
//    return autoCast(FieldUtils.readField(processorLogObserver, fieldName, true));
//  }
//
//  public static ProcessorNode getProcessorNode(StandardProcessContext standardProcessContext) throws Exception {
//
//    String fieldName = findFieldName(ProcessorNode.class, StandardProcessContext.class);
//    return autoCast(FieldUtils.readField(standardProcessContext, fieldName, true));
//  }
//
//  public static RepositoryContext getRepositoryContext(StandardProcessSession standardProcessSession) throws Exception {
//
//    String fieldName = findFieldName(RepositoryContext.class, StandardProcessSession.class);
//    return autoCast(FieldUtils.readField(standardProcessSession, fieldName, true));
//  }
//
//  public static StandardProcessSessionFactory toStandardProcessSessionFactory(ProcessSessionFactory processSessionFactory) throws Exception {
//
//    String fieldName = findFieldName(StandardProcessSessionFactory.class, processSessionFactory);
//    return autoCast(FieldUtils.readField(processSessionFactory, fieldName, true));
//  }
//
//  public static StandardRepositoryContext toStandardRepositoryContext(StandardProcessSessionFactory standardProcessSessionFactory) throws Exception {
//
//    String fieldName = findFieldName(StandardRepositoryContext.class, StandardProcessSessionFactory.class);
//    return autoCast(FieldUtils.readField(standardProcessSessionFactory, fieldName, true));
//  }
//
//  public static Identity extractProcessorNode(ProcessorNode processorNode) {
//    return new Identity(processorNode.getProcessor().getIdentifier(), processorNode.getResource().getName());
//  }
//
//  public static Identity getComponentIdentity(StandardProcessSession standardProcessSession) throws Exception {
//    return new Identity(getRepositoryContext(standardProcessSession).getConnectable());
//  }
//
//  public static Identity getComponentIdentity(StandardProcessContext standardProcessContext) throws Exception {
//    return extractProcessorNode(getProcessorNode(standardProcessContext));
//  }
//
//  public static Identity getComponentIdentity(LogObserver logObserver) throws Exception {
//    return extractProcessorNode(getProcessorNode((ProcessorLogObserver) logObserver));
//  }
//
//  public static Optional<StandardRepositoryRecord> findStandardRepositoryRecord(StandardProcessSession standardProcessSession, FlowFile flowFile) throws Exception {
//
//    return getRecordMap(standardProcessSession).values().stream()
//    .filter(s -> s.getCurrent().getAttribute(CoreAttributes.UUID.key()).equals(flowFile.getAttribute(CoreAttributes.UUID.key())))
//    .findAny();
//  }
//
//  public static boolean isParent(ProcessSession processSession, FlowFile flowFile) throws Exception {
//    return isParent(findStandardRepositoryRecord(Insight.autoCast(processSession), flowFile).get());
//  }
//
//  public static boolean isParent(StandardRepositoryRecord standardRepositoryRecord) {
//    return standardRepositoryRecord.getType() == RepositoryRecordType.CREATE;
//  }
//
//  @SuppressWarnings("TypeParameterUnusedInFormals")
//  public static <T> T autoCast(Object object){
//    return (T) object;
//  }
//
//  @org.jetbrains.annotations.Nullable
//  public static Class<?> safeToClass(String classPath) {
//
//    try {
//
//      return Class.forName(classPath);
//    }
//    catch (ClassNotFoundException e) {
//
//      AgentLogger.error(State.NULL, e);
//    }
//
//    return null;
//  }
//}