//package io.opentelemetry.instrumentation.nifi.v1_24_0.model;
//
//import java.util.HashMap;
//import java.util.Map;
//import java.util.Map.Entry;
//import java.util.Optional;
//import java.util.Set;
//
//import io.opentelemetry.instrumentation.nifi.v1_24_0.util.Insight;
//import org.apache.nifi.components.PropertyDescriptor;
//import org.apache.nifi.connectable.Connectable;
//import org.apache.nifi.connectable.ConnectableType;
//import org.apache.nifi.connectable.Connection;
//import org.apache.nifi.controller.ProcessorNode;
//import org.apache.nifi.controller.StandardProcessorNode;
//import org.apache.nifi.controller.repository.RepositoryContext;
//import org.apache.nifi.controller.repository.StandardProcessSession;
//import org.apache.nifi.groups.ProcessGroup;
//import org.apache.nifi.processor.ProcessSession;
//import org.apache.nifi.processor.StandardProcessContext;
//import org.apache.nifi.scheduling.SchedulingStrategy;
//
//import org.jetbrains.annotations.Nullable;
//
//public class DynamicDetails {
//
//  @Nullable
//  private String label = null;
//  @Nullable
//  private String comments = null;
//  @Nullable
//  private ConnectableType connectableType = null;
//  @Nullable
//  private ProcessGroup processGroup = null;
//  @Nullable
//  private SchedulingStrategy schedulingStrategy = null;
//  @Nullable
//  private Set<Connection> connections = null;
//  private Map<String, String> processorPropertyMap = new HashMap<>();
//
//  public DynamicDetails(StandardProcessContext standardProcessContext) throws Exception {
//
//    ProcessorNode processorNode = Insight.getProcessorNode(standardProcessContext);
//
//    this.comments = processorNode.getComments();
//    this.connectableType = processorNode.getConnectableType();
//    this.connections = processorNode.getConnections();
//    this.processGroup = processorNode.getProcessGroup();
//    this.schedulingStrategy = processorNode.getSchedulingStrategy();
//    this.label = processorNode.getName();
//    this.processorPropertyMap = standardProcessContext.getAllProperties();
//  }
//
//  public DynamicDetails(ProcessSession processSession) throws Exception {
//
//    StandardProcessSession standardProcessSession = (StandardProcessSession) processSession;
//
//    RepositoryContext repositoryContext = Insight.getRepositoryContext(standardProcessSession);
//    Connectable connectable = repositoryContext.getConnectable();
//
//    if(connectable.getConnectableType() == ConnectableType.PROCESSOR) {
//
//      StandardProcessorNode standardProcessorNode = (StandardProcessorNode) connectable;
//      Map<PropertyDescriptor, String> propertyMap = standardProcessorNode.getRawPropertyValues();
//
//      for(Entry<PropertyDescriptor, String> entry : propertyMap.entrySet()) {
//
//        String value = Optional.ofNullable(entry.getValue()).orElse(entry.getKey().getDefaultValue());
//
//        processorPropertyMap.put(entry.getKey().getDisplayName(), value);
//      }
//    }
//
//    this.comments = connectable.getComments();
//    this.connectableType = connectable.getConnectableType();
//    this.connections = connectable.getConnections();
//    this.processGroup = connectable.getProcessGroup();
//    this.schedulingStrategy = connectable.getSchedulingStrategy();
//    this.label = connectable.getName();
//  }
//
//  public DynamicDetails(Connectable connectable) {
//
//    if(connectable.getConnectableType() == ConnectableType.PROCESSOR) {
//
//      StandardProcessorNode standardProcessorNode = (StandardProcessorNode) connectable;
//      Map<PropertyDescriptor, String> propertyMap = standardProcessorNode.getRawPropertyValues();
//
//      for(Entry<PropertyDescriptor, String> entry : propertyMap.entrySet()) {
//
//        String value = Optional.ofNullable(entry.getValue()).orElse(entry.getKey().getDefaultValue());
//
//        processorPropertyMap.put(entry.getKey().getDisplayName(), value);
//      }
//    }
//
//    this.comments = connectable.getComments();
//    this.connectableType = connectable.getConnectableType();
//    this.connections = connectable.getConnections();
//    this.processGroup = connectable.getProcessGroup();
//    this.schedulingStrategy = connectable.getSchedulingStrategy();
//    this.label = connectable.getName();
//  }
//
//  public @org.jetbrains.annotations.Nullable
//  String getLabel() {
//    return label;
//  }
//
//  public void setLabel(@org.jetbrains.annotations.Nullable String label) {
//    this.label = label;
//  }
//
//  public @org.jetbrains.annotations.Nullable String getComments() {
//    return comments;
//  }
//
//  public void setComments(@org.jetbrains.annotations.Nullable String comments) {
//    this.comments = comments;
//  }
//
//  public @org.jetbrains.annotations.Nullable ConnectableType getConnectableType() {
//    return connectableType;
//  }
//
//  public void setConnectableType(@org.jetbrains.annotations.Nullable ConnectableType connectableType) {
//    this.connectableType = connectableType;
//  }
//
//  public @org.jetbrains.annotations.Nullable ProcessGroup getProcessGroup() {
//    return processGroup;
//  }
//
//  public void setProcessGroup(@org.jetbrains.annotations.Nullable ProcessGroup processGroup) {
//    this.processGroup = processGroup;
//  }
//
//  public void setSchedulingStrategy(@org.jetbrains.annotations.Nullable SchedulingStrategy schedulingStrategy) {
//    this.schedulingStrategy = schedulingStrategy;
//  }
//
//  public @org.jetbrains.annotations.Nullable SchedulingStrategy getSchedulingStrategy() {
//    return schedulingStrategy;
//  }
//
//  public @org.jetbrains.annotations.Nullable Set<Connection> getConnections() {
//    return connections;
//  }
//
//  public void setConnections(@org.jetbrains.annotations.Nullable Set<Connection> connections) {
//    this.connections = connections;
//  }
//
//  public Map<String, String> getProcessorPropertyMap() {
//    return processorPropertyMap;
//  }
//
//  public void setProcessorPropertyMap(Map<String, String> processorPropertyMap) {
//    this.processorPropertyMap = processorPropertyMap;
//  }
//}