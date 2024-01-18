package io.opentelemetry.instrumentation.nifi.v1_24_0.model;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.Locale;

import org.apache.nifi.connectable.Connectable;
import org.apache.nifi.connectable.ConnectableType;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.controller.repository.StandardProcessSession;
import org.apache.nifi.processor.Processor;
//import org.apache.nifi.processor.util.bin.BinFiles;
import org.apache.nifi.controller.repository.RepositoryContext;
import org.apache.commons.lang3.reflect.FieldUtils;
import java.lang.reflect.Field;

@SuppressWarnings("NullAway")
public class StandardIdentifier {

    public enum ComponentSessionType {MULTI_SESSION, SINGLE_SESSION}

    private String uuid = null;
    private String name = null;
    private Long sessionId = null;
    private boolean isMultiSessionProcessor = false;
    private Connectable connectable = null;
    private ConnectableType connectableType = null;
    private ComponentSessionType componentSessionType = null;
    private StandardProcessSession standardProcessSession = null;

    public StandardIdentifier(StandardProcessSession standardProcessSession) throws Exception {

        this.standardProcessSession = standardProcessSession;
        this.sessionId = getSessionId(standardProcessSession);
        this.connectable = getConnectable(standardProcessSession);
        this.connectableType = connectable.getConnectableType();
        this.isMultiSessionProcessor = isBinFileProcessor(connectable);
        this.componentSessionType = getComponentSessionType(standardProcessSession);
        this.uuid = connectable.getIdentifier();
        this.name = StandardIdentifier.getComponentName(connectable);
    }

    public StandardIdentifier(ProcessSession processSession) throws Exception {

      try {

          StandardProcessSession standardProcessSession = (StandardProcessSession) processSession;

          System.out.println(">>>>>> DEBUG : In StandardIdentifier : " + standardProcessSession.toString());

          this.standardProcessSession = standardProcessSession;
          this.sessionId = getSessionId(standardProcessSession);
          this.connectable = getConnectable(standardProcessSession);
          this.connectableType = connectable.getConnectableType();
          this.isMultiSessionProcessor = isBinFileProcessor(connectable);
          this.componentSessionType = getComponentSessionType(standardProcessSession);
          this.uuid = connectable.getIdentifier();
          this.name = StandardIdentifier.getComponentName(connectable);

          System.out.println(">>>>>> DEBUG : In StandardIdentifier : " + this.toString());
      }
      catch (ClassCastException e) {

          System.out.println(">>>>>> DEBUG : PROBLEM !! In Connectable block : " + e.getMessage());
      }
    }

    public String getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public static Long getSessionId(StandardProcessSession standardProcessSession) throws Exception {
        return (Long) FieldUtils.readField(standardProcessSession, "sessionId", true);
    }

    public boolean isMultiSessionProcessor() {
        return isMultiSessionProcessor;
    }

    public static boolean isMultiSessionProcessor(StandardProcessSession standardProcessSession) throws Exception {

        Connectable connectable = getConnectable(standardProcessSession);
        return isBinFileProcessor(connectable);
    }

    public Connectable getConnectable() {
        return connectable;
    }

    public static Connectable getConnectable(StandardProcessSession standardProcessSession) throws Exception {

        RepositoryContext repositoryContext = getRepositoryContext(standardProcessSession);
        return repositoryContext.getConnectable();
    }

    public ConnectableType getConnectableType() {
        return connectableType;
    }

    public ComponentSessionType getComponentSessionType() {
        return componentSessionType;
    }

    public static ComponentSessionType getComponentSessionType(StandardProcessSession standardProcessSession) throws Exception {

        if(isMultiSessionProcessor(standardProcessSession)) {
            return ComponentSessionType.MULTI_SESSION;
        }
        else {
            return ComponentSessionType.SINGLE_SESSION;
        }
    }

    public StandardProcessSession getStandardProcessSession() {
        return standardProcessSession;
    }

    public static boolean isProcessorConnectableType(StandardProcessSession standardProcessSession) throws Exception {

        Connectable connectable = getConnectable(standardProcessSession);
        return connectable.getConnectableType().equals(ConnectableType.PROCESSOR);
    }

    public static boolean isBinFileProcessor(Connectable connectable) {

        if (connectable.getConnectableType().equals(ConnectableType.PROCESSOR)) {

           Object object = connectable.getRunnableComponent();

           if(object instanceof Processor) {

               Processor processor = (Processor) object;
               System.out.println(">>>>>> DEBUG IN-BINFILES-METHOD : " + processor.getIdentifier());

               //String x = org.apache.nifi.processor.util.bin.BinFiles.class.getName();
               
               // IF UNCOMMENT TWO LINES BELOW, MISSING CLASS OCCURS
               //String x = org.apache.nifi.processors.standard.LogAttribute.class.getName();

               //System.out.println(">>>>> DEBUG : BIN FILES (MERGE CONTENT) = " + x);

               //return processor instanceof BinFiles;
           }
        }

        return false;

//        return Stream.of(connectable)
//        .filter(c -> c.getConnectableType().equals(ConnectableType.PROCESSOR))
//        .map(Connectable::getRunnableComponent)
//        .filter(Processor.class::isInstance)
//        .map(Processor.class::cast)
//        .anyMatch(BinFiles.class::isInstance);
    }

    public static RepositoryContext getRepositoryContext(StandardProcessSession standardProcessSession) throws IllegalAccessException {

        String fieldName = findFieldName(RepositoryContext.class, StandardProcessSession.class);
        return (RepositoryContext) FieldUtils.readField(standardProcessSession, fieldName, true);
    }

    public static String findFieldName(Class<?> target, Class<?> clazz) {

        return Arrays.stream(clazz.getDeclaredFields())
        .filter(f -> f.getType().getSimpleName().equals(target.getSimpleName()) || f.getType().isAssignableFrom(target))
        .findFirst()
        .map(Field::getName).orElse("UNKNOWN");
    }

    public static String getComponentName(Connectable connectable) {

        if(connectable.getConnectableType().equals(ConnectableType.PROCESSOR)) {
          return connectable.getComponentType();
        }
        else if(connectable.getConnectableType().equals(ConnectableType.FUNNEL)) {
          return connectable.getComponentType();
        }
        else {
          return normalizeComponentPortName(connectable);
        }
    }

    private static String normalizeComponentPortName(Connectable connectable) {

        return Stream.of(connectable)
        .map(Connectable::getConnectableType)
        .map(ConnectableType::name)
        .map(s -> s.split("_"))
        .map(Arrays::asList)
        .flatMap(List::stream)
        .map(String::toLowerCase)
        .map(s -> s.substring(0, 1).toUpperCase(Locale.ENGLISH) + s.substring(1))
        .collect(Collectors.joining());
    }
}
