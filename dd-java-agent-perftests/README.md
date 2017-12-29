# Datadog Java Agent Performance Tests
Integration level performance tests for the Datadog Java Agent.

## Running a Test
1. Build the shadow jar for the server you wish to test against.
2. Run the performance test script passing in the agent jars you wish to test.
3. (optional) Save test results csv and ponder the great mysteries of performance optimization.

### Example
```
./gradlew dd-java-agent-perftests:jetty-perftest:shadowJar
./run-perf-test.sh jetty-perftest/build/libs/jetty-perftest-0.2.12-SNAPSHOT-all.jar NoAgent ../dd-java-agent/build/libs/dd-java-agent-0.2.12-SNAPSHOT.jar
cp /tmp/perf_results.csv ~/somewhere_else/
```
