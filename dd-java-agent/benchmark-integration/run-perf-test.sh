#!/usr/bin/env bash

# A script for measuring a server's throughput with or without a java agent.
test_csv_file=/tmp/perf_results.csv
server_output=/tmp/server_output.txt
server_type=$1
server_package=$2
agent_jars="${@:3}"
server_pid=""
agent_pid=$(lsof -i tcp:8126 | awk '$8 == "TCP" { print $2 }')
if [[ "$server_package" = "" ]] || [[ "$server_type" != "play-zip" && "$server_type" != "jar" ]]; then
    echo "usage: ./run-perf-test.sh [play-zip|jar] path-to-server-package path-to-agent1 path-to-agent2..."
    echo ""
    echo "[play-zip|jar] : Specify whether the server will be in zip format (play server) or jar format"
    echo "path-to-server-package : Must be a jar or binary zip package which creates an http server on local port 8080 when started."
    echo "    Note: if the server package is a zip, then the script will attempt to unzip to a temp directory and run the server from there."
    echo "path-to-agent*     : Each must be a javaagent jar, or NoAgent."
    echo ""
    echo "Example: This will run the perf tests against myserver.jar. It will run against no agent as a baseline, then against myagent-1.0.jar."
    echo "  ./run-perf-test.sh /tmp/myserve.jar NoAgent /tmp/myagent-1.0.jar"
    echo ""
    echo "Test results are saved to $test_csv_file"
    exit 1
fi

if [ "$agent_pid" = "" ]; then
    echo "discarding traces"
    writer_type="LoggingWriter"
else
    echo "sending traces to local trace agent: $agent_pid"
    writer_type="DDAgentWriter"
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

unzipped_server_path=""

# Start up server passed into the script
# Blocks until server is bound to local port 8080
function start_server {
    agent_jar="$1"
    javaagent_arg=""
    if [ "$agent_jar" != "" -a -f "$agent_jar" ]; then
        javaagent_arg="-javaagent:$agent_jar -Ddatadog.slf4j.simpleLogger.defaultLogLevel=off -Ddd.writer.type=$writer_type -Ddd.service.name=perf-test-app"
    fi

    if [ "$server_type" = "jar" ]; then
      echo "starting server: java $javaagent_arg -jar $server_package"
      { /usr/bin/time -l java $javaagent_arg -Xms256m -Xmx256m -jar $server_package ; } 2> $server_output  &
    else
      # make a temp directory to hold the unzipped server
      unzip_temp=`mktemp -d`
      # perform the unzipping of the play zip
      unzip ${server_package} -d ${unzip_temp} &> /dev/null

      if [ $? -eq 0 ]; then
        echo "Unzipped server package at ${unzip_temp}"
      else
        echo "Failed to unzip server package to ${unzip_temp}"
        exit 2
      fi

      # get the unzipped directory name
      unzipped_dirname=`basename ${unzip_temp}/*`
      # unzipped server location, will be removed when the server is stopped
      unzipped_server_path=${unzip_temp}

      java_opts_env='JAVA_OPTS="'${javaagent_arg}'"'
      # it appears the binary script will always be named playBinary at the time of writing
      # no matter what the zip file is named.
      play_script=${unzipped_server_path}/${unzipped_dirname}/bin/playBinary

      # have to use env to set JAVA_OPTS because of a gradle play plugin bug:
      # https://github.com/gradle/gradle/issues/4471
      if [ "$agent_jar" != "" -a -f "$agent_jar" ]; then
        utility_cmd="env JAVA_OPTS=${javaagent_arg} ${play_script}"
      else
        utility_cmd="${play_script}"
      fi

      echo "starting server: ${utility_cmd}"
      { /usr/bin/time -l ${utility_cmd} ; } 2> $server_output &
    fi

    # Block until server is up
    until nc -z localhost 8080; do
        sleep 0.5
    done
    server_pid=$(lsof -i tcp:8080 | awk '$8 == "TCP" { print $2 }' | uniq)
    echo "server $server_pid started"
}

# Send a kill signal to the running server
# and block until the server is stopped
function stop_server {
    echo "Stopping $server_pid"
    kill $server_pid
    wait
    server_pid=""
    # clean up the unzipped server
    if [[ ${unzipped_server_path} != "" ]] && [[ ${server_type} = "play-zip" ]] && [[ -d ${unzipped_server_path} ]]; then
      echo "Cleaning up unzipped server at "${unzipped_server_path}
      rm -rf ${unzipped_server_path}
    fi
}

# Warmup and run wrk tests on a single endpoint.
# echos out a file containing raw wrk output
# and a final line of the average requests/second
function test_endpoint {
    url=$1
    # warmup
    wrk -c $test_num_connections -t$test_num_threads -d ${test_warmup_seconds}s $url >/dev/null

    # run test
    wrk_results=/tmp/wrk_results.`date +%s`
    wrk -c $test_num_connections -t$test_num_threads -d ${test_time_seconds}s $url > $wrk_results
    echo $wrk_results
}


trap 'stop_server; exit' SIGINT
trap 'kill $server_pid; exit' SIGTERM
header='Client Version'
for label in "${test_order[@]}"; do
    header="$header,$label Latency,$label Throughput"
done
header="$header,Agent CPU Burn,Server CPU Burn,Agent RSS Delta,Server Max RSS,Server Start RSS,Server Load Increase RSS"
echo $header > $test_csv_file

for agent_jar in $agent_jars; do
    echo "----Testing agent $agent_jar----"
    if [ "$agent_jar" == "NoAgent" ]; then
        result_row="NoAgent"
        start_server ""
    else
        agent_version=$(java -jar $agent_jar 2>/dev/null)
        result_row="$agent_version"
        start_server $agent_jar
    fi


    if [ "$agent_pid" = "" ]; then
        agent_start_cpu=0
        agent_start_rss=0
    else
        agent_start_cpu=$(ps -o 'pid,time' | awk "\$1 == $agent_pid { print \$2 }" | awk -F'[:\.]' '{ print ($1 * 3600) + ($2 * 60) + $3 }')
        agent_start_rss=$(ps -o 'pid,rss' | awk "\$1 == $agent_pid { print \$2 }")
    fi
    server_start_cpu=$(ps -o 'pid,time' | awk "\$1 == $server_pid { print \$2 }" | awk -F'[:\.]' '{ print ($1 * 3600) + ($2 * 60) + $3 }')
    server_start_rss=$(ps -o 'pid,rss' | awk "\$1 == $server_pid { print \$2 }")

    server_total_rss=0
    server_total_rss_count=0

    for t in "${test_order[@]}"; do
        label="$t"
        url="${endpoints[$label]}"
        echo "--Testing $label -- $url--"
        test_output_file=$(test_endpoint $url)
        let server_total_rss=$server_total_rss+$(ps -o 'pid,rss' | awk "\$1 == $server_pid { print \$2 }")
        let server_total_rss_count=$server_total_rss_count+1
        cat $test_output_file
        avg_latency=$(awk '$1 == "Latency" { print $2 }' $test_output_file)
        avg_throughput=$(awk '$1 == "Requests/sec:" { print $2 }' $test_output_file)
        result_row="$result_row,$avg_latency,$avg_throughput"
        rm $test_output_file
    done

    if [ "$agent_pid" = "" ]; then
        agent_stop_cpu=0
        agent_stop_rss=0
    else
        agent_stop_cpu=$(ps -o 'pid,time' | awk "\$1 == $agent_pid { print \$2 }" | awk -F'[:\.]' '{ print ($1 * 3600) + ($2 * 60) + $3 }')
        agent_stop_rss=$(ps -o 'pid,rss' | awk "\$1 == $agent_pid { print \$2 }")
    fi
    server_stop_cpu=$(ps -o 'pid,time' | awk "\$1 == $server_pid { print \$2 }" | awk -F'[:\.]' '{ print ($1 * 3600) + ($2 * 60) + $3 }')

    let agent_cpu=$agent_stop_cpu-$agent_start_cpu
    let agent_rss=$agent_stop_rss-$agent_start_rss
    let server_cpu=$server_stop_cpu-$server_start_cpu

    server_load_increase_rss=$(echo "scale=2; ( $server_total_rss / $server_total_rss_count ) - $server_start_rss" | bc)

    stop_server

    server_max_rss=$(awk '/.* maximum resident set size/ { print $1 }' $server_output)
    rm $server_output

    echo "$result_row,$agent_cpu,$server_cpu,$agent_rss,$server_max_rss,$server_start_rss,$server_load_increase_rss" >> $test_csv_file
    echo "----/Testing agent $agent_jar----"
    echo ""
done

echo ""
echo "DONE. Test results saved to $test_csv_file"
