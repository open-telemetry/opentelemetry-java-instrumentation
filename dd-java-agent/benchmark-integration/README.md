# Datadog Java Agent Performance Tests
Integration level performance tests for the Datadog Java Agent.

## Perf Script Dependencies

`run-perf-test.sh` requires the following (available on homebrew or a linux package manager):

* bash (>=4.0)
* wrk
* nc

## Running a Test
1. Build the shadow jar or the distribution zip for the server you wish to test against.
2. Run the performance test script passing in the agent jars you wish to test.
3. (optional) Save test results csv and ponder the great mysteries of performance optimization.

### Example
#### Jetty
```
./gradlew dd-java-agent:benchmark-integration:jetty-perftest:shadowJar
# Compare a baseline (no agent) to the 0.18.0 and 0.19.0 releases.
/usr/local/bin/bash ./run-perf-test.sh jar jetty-perftest/build/libs/jetty-perftest-*-all.jar NoAgent ~/Downloads/dd-java-agent-0.18.0.jar ~/Downloads/dd-java-agent-0.19.0.jar
cp /tmp/perf_results.csv ~/somewhere_else/
```
#### Play
```
./gradlew :dd-java-agent:benchmark-integration:play-perftest:dist
# Compare a baseline (no agent) to the 0.18.0 and 0.19.0 releases.
/usr/local/bin/bash ./run-perf-test.sh play-zip play-perftest/build/distributions/playBinary NoAgent ~/Downloads/dd-java-agent-0.18.0.jar ~/Downloads/dd-java-agent-0.19.0.jar
cp /tmp/perf_results.csv ~/somewhere_else/
```
