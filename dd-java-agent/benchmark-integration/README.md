# Datadog Java Agent Performance Tests
Integration level performance tests for the Datadog Java Agent.

## Perf Script Dependencies

`run-perf-test.sh` requires the following (available on homebrew or a linux package manager):

* bash (>=4.0)
* wrk
* nc

## Running a Test
1. Build the shadow jar for the server you wish to test against.
2. Run the performance test script passing in the agent jars you wish to test.
3. (optional) Save test results csv and ponder the great mysteries of performance optimization.

### Example
```
./gradlew dd-java-agent:benchmark-integration:jetty-perftest:shadowJar
# Compare a baseline (no agent) to the 0.2.10 and 0.2.9 releases.
/usr/local/bin/bash ./run-perf-test.sh jetty-perftest/build/libs/jetty-perftest-*-all.jar NoAgent ../benchmark/releases/dd-java-agent-0.2.10.jar ../benchmark/releases/dd-java-agent-0.2.9.jar
cp /tmp/perf_results.csv ~/somewhere_else/
```
