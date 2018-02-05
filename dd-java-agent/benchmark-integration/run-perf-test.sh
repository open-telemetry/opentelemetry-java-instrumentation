#!/bin/sh

# A script for measuring a server's throughput with or without a java agent.
test_csv_file=/tmp/perf_results.csv
server_jar=$1
agent_jars="${@:2}"
server_pid=""

if [ "$server_jar" = "" ]; then
    echo "usage: ./run-perf-test.sh path-to-server-jar path-to-agent1 path-to-agent2..."
    echo ""
    echo "path-to-server-jar : Must be a jar which creates an http server on local port 8080 when started as a jar."
    echo "path-to-agent*     : Each must be a javaagent jar, or NoAgent."
    echo ""
    echo "Example: This will run the perf tests against myserver.jar. It will run against no agent as a baseline, then against myagent-1.0.jar."
    echo "  ./run-perf-test.sh /tmp/myserve.jar NoAgent /tmp/myagent-1.0.jar"
    echo ""
    echo "Test results are saved to $test_csv_file"
    exit 1
fi

if [ -f perf-test-settings.rc ]; then
    echo "loading custom settings"
    cat ./perf-test-settings.rc
    . ./perf-test-settings.rc
else
    echo "loading default settings"
    cat ./perf-test-default-settings.rc
    . ./perf-test-default-settings.rc
fi
echo ""
echo ""

# Start up server jar passed into the scipt
# Blocks until server is bound to local port 8080
function start_server {
    agent_jar="$1"
    javaagent_arg=""
    if [ "$agent_jar" != "" -a -f "$agent_jar" ]; then
        javaagent_arg="-javaagent:$agent_jar -Ddatadog.slf4j.simpleLogger.defaultLogLevel=off -Ddd.writer.type=LoggingWriter -Ddd.service.name=perf-test-app"
    fi
    echo "starting server: java $javaagent_arg -jar $server_jar"
    java $javaagent_arg -jar $server_jar &
    server_pid=$!
    # Block until server is up
    until nc -z localhost 8080; do
        sleep 0.5
    done
}

# Send a kill signal to the running server
# and block until the server is stopped
function stop_server {
    kill $server_pid
    wait
    server_pid=""
}

# Warmup and run wrk tests on a single endpoint.
# echos out a file containing raw wrk output
# and a final line of the average requests/second
function test_endpoint {
    url=$1
    # warmup
    wrk -c $test_num_connections -t$test_num_threads -d ${test_warmup_seconds}s $url >/dev/null
    wrk_results=/tmp/wrk_results.`date +%s`
    # run test
    wrk -c $test_num_connections -t$test_num_threads -d ${test_time_seconds}s $url > $wrk_results
    avg_throughput=$(grep "Requests/sec" $wrk_results | grep -o "[0-9.]*$")
    echo "$avg_throughput" >> $wrk_results
    echo $wrk_results
}


trap 'stop_server; exit' SIGINT
trap 'kill $server_pid; exit' SIGTERM
header='Client Version'
for label in "${test_order[@]}"; do
    header="$header,$label"
done
echo $header > $test_csv_file

for agent_jar in $agent_jars; do
    echo "----Testing agent $agent_jar----"
    if [ "$agent_jar" == "NoAgent" ]; then
        result_row="NoAgent"
        start_server ""
    else
        # agent_version=$(java -jar $agent_jar 2>/dev/null | grep -o "^[^~]*")
        agent_version=$(java -jar $agent_jar 2>/dev/null)
        result_row="$agent_version"
        start_server $agent_jar
    fi

    for t in "${test_order[@]}"; do
        label="$t"
        url="${endpoints[$label]}"
        echo "--Testing $label -- $url--"
        test_output_file=$(test_endpoint $url)
        cat $test_output_file
        throughput=$(tail -n 1 $test_output_file)
        result_row="$result_row,$throughput"
        rm $test_output_file
    done
    echo "$result_row" >> $test_csv_file
    stop_server
    echo "----/Testing agent $agent_jar----"
done

echo ""
echo "DONE. Test results saved to $test_csv_file"
