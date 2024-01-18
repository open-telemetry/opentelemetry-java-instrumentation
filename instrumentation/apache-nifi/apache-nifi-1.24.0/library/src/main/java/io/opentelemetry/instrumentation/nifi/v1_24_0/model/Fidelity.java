//package io.opentelemetry.instrumentation.nifi.v1_24_0.model;
//
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//import java.util.Objects;
//import java.util.stream.Collectors;
//import org.apache.nifi.controller.repository.RepositoryRecordType;
//import org.apache.nifi.controller.repository.StandardRepositoryRecord;
//
//import io.opentelemetry.instrumentation.nifi.v1_24_0.model.StandardIdentifier.ComponentSessionType;
//
//public enum Fidelity {
//
//  ONE_TO_ONE,
//  ONE_TO_MANY,
//  MANY_TO_ONE,
//  MANY_TO_MANY;
//
//  private static final Map<String, Fidelity> fidelityMap = new HashMap<>();
//
//  public static Fidelity toFidelity(StandardIdentifier standardIdentifier, List<StandardRepositoryRecord> repositoryRecords) throws Exception {
//
//    String componentId = standardIdentifier.getIdentity().getName().concat("-").concat(standardIdentifier.getIdentity().getId());
//
//    if(fidelityMap.containsKey(componentId)) {
//      return fidelityMap.get(componentId);
//    }
//
//    Map<RepositoryRecordType, List<StandardRepositoryRecord>> groupedRecords = repositoryRecords.stream()
//    .collect(Collectors.groupingBy(StandardRepositoryRecord::getType));
//
//    List<StandardRepositoryRecord> createRecords = groupedRecords.get(RepositoryRecordType.CREATE);
//    List<StandardRepositoryRecord> updateRecords = groupedRecords.get(RepositoryRecordType.UPDATE);
//
//    ComponentSessionType componentSessionType = standardIdentifier.getComponentSessionType();
//
//    boolean eitherIsNull = (createRecords == null || updateRecords == null);
//
//    if(eitherIsNull && componentSessionType == ComponentSessionType.SINGLE_SESSION) {
//
//      fidelityMap.put(componentId, Fidelity.ONE_TO_ONE);
//
//      return Fidelity.ONE_TO_ONE;
//    }
//
//    if(updateRecords == null) {
//
//      throw new Exception("Records were null");
//    }
//
//    Fidelity fidelity = getFidelity(Objects.requireNonNull(createRecords), updateRecords, componentSessionType);
//
//    fidelityMap.put(componentId, fidelity);
//
//    return fidelity;
//  }
//
//  public static Fidelity getFidelity(List<StandardRepositoryRecord> createRecords, @org.jetbrains.annotations.NotNull List<StandardRepositoryRecord> updateRecords, ComponentSessionType componentSessionType) {
//
//    Fidelity fidelity;
//    boolean hasRecordsInBoth = !createRecords.isEmpty() && !updateRecords.isEmpty();
//
//    if(componentSessionType == ComponentSessionType.SINGLE_SESSION && hasRecordsInBoth) {
//      fidelity = Fidelity.ONE_TO_MANY;
//    }
//    else if(componentSessionType == ComponentSessionType.MULTI_SESSION && hasRecordsInBoth) {
//      fidelity = Fidelity.MANY_TO_ONE;
//    }
//    else if(componentSessionType == ComponentSessionType.SINGLE_SESSION) {
//      fidelity = Fidelity.ONE_TO_ONE;
//    }
//    else {
//      fidelity = Fidelity.MANY_TO_MANY;
//    }
//
//    return fidelity;
//  }
//}