package io.opentelemetry.javaagent.instrumentation.nifi.v1_24_0;

//import io.opentelemetry.instrumentation.nifi.v1_24_0.util.Insight;
//import io.opentelemetry.instrumentation.nifi.v1_24_0.NifiTelemetry;
//import io.opentelemetry.instrumentation.nifi.v1_24_0.util.AgentLogger;
//import io.opentelemetry.instrumentation.nifi.v1_24_0.util.EventTracker;
//import io.opentelemetry.instrumentation.nifi.v1_24_0.model.StandardIdentifier;
//import io.opentelemetry.instrumentation.nifi.v1_24_0.model.State;
//import io.opentelemetry.instrumentation.nifi.v1_24_0.model.Nifi;
//import io.opentelemetry.instrumentation.nifi.v1_24_0.model.DynamicDetails;
//import io.opentelemetry.instrumentation.nifi.v1_24_0.model.Fidelity;
//import org.apache.nifi.controller.repository.StandardRepositoryRecord;
import org.apache.nifi.processor.ProcessSession;
import io.opentelemetry.instrumentation.nifi.v1_24_0.model.StandardIdentifier;
//import org.apache.commons.collections4.CollectionUtils;
//
//import java.util.Comparator;
//import java.util.List;
//import java.util.Map;
//import java.util.stream.Collectors;

public final class NifiSingleton {

    private NifiSingleton() {}

    public static void commitSession(ProcessSession processSession) {

        System.out.println(">>>>>>>> DEBUG : NifiSingleton.commitSession");

        StandardIdentifier stndr = null;
        try {
            stndr = new StandardIdentifier(processSession);
            System.out.println(">>>>>>>> DEBUG : NifiSingleton.commitSession standardIdentifier : " + stndr.toString());

        } catch (Exception e) {
            System.out.println(">>>>>>>> DEBUG : ISSUE NifiSingleton.commitSession "  + e.getMessage());
        }



//        try {
//
//            StandardIdentifier standardIdentifier = new StandardIdentifier(processSession);
//
//            List<StandardRepositoryRecord> records = Insight.getStandardRepositoryRecord(processSession).stream()
//            .sorted(Comparator.comparing(record -> record.getCurrent().getEntryDate()))
//            .filter(record -> Nifi.hasNotVisited(standardIdentifier, record.getCurrent()))
//            .collect(Collectors.toList());
//
//            if(records.isEmpty()) {
//                return;
//            }
//
//            Map<Nifi.Evaluation, List<StandardRepositoryRecord>> siftedRecords = Nifi.group(records);
//
//            if(siftedRecords.containsKey(Nifi.Evaluation.DEAD)) {
//                EventTracker.pruneEvents(standardIdentifier, siftedRecords.get(Nifi.Evaluation.DEAD), EventTracker.EventType.LOG_MESSAGE);
//            }
//
//            List<StandardRepositoryRecord> activeRecords = siftedRecords.get(Nifi.Evaluation.ACTIVE);
//
//            if(CollectionUtils.isEmpty(activeRecords)) {
//                return;
//            }
//
//            DynamicDetails dynamicDetails = new DynamicDetails(processSession);
//
//            Fidelity fidelity = Fidelity.toFidelity(standardIdentifier, records);
//            NifiTelemetry.computeTracing(fidelity, standardIdentifier, dynamicDetails, activeRecords);
//
//            AgentLogger.log(State.COMMIT_SESSION, standardIdentifier.getIdentity().toString());
//        }
//        catch (Exception e) {
//
//            AgentLogger.error(State.COMMIT_SESSION, e);
//        }
    }
}
