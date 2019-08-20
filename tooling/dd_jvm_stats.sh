#!/usr/bin/env bash

set -e

OUTPUT_ROOT=$(pwd)

# Thread dump interval defaults to 10 mins
THREAD_DUMP_INTERVAL_SECS=600
# Heap dump interval defaults to 30 mins
HEAP_DUMP_INTERVAL_SECS=1800
# GC usage interval defaults to 30 secs
GC_INTERVAL_SECS=30
# Runtime stats interval defaults to 10 secs
RUNTIME_INTERVAL_SECS=10
# Default runs continuously
STOP_AFTER_SECS=0

function usage {
  echo "Usage: ./dd_jvm_stats --pid <PID> [--STOP_AFTER_SECS <SECS:0>] [--runtime-interval <SECS:10>] [--thread-interval <SECS:600>] [--heap-interval <SECS:1800>] [--gc-interval <SECS:30>] [--runtime-interval <SECS:10>] [--output <PATH:./>]"
}

while [[ "$1" != "" ]]; do
    case $1 in
        --pid )                 shift
                                PROCESS_IDENTIFIER=$1
                                ;;
        --output )              shift
                                OUTPUT_ROOT=$1
                                ;;
        --thread-interval )     shift
                                THREAD_DUMP_INTERVAL_SECS=$1
                                ;;
        --heap-interval )       shift
                                HEAP_DUMP_INTERVAL_SECS=$1
                                ;;
        --gc-interval )         shift
                                GC_INTERVAL_SECS=$1
                                ;;
        --runtime-interval )    shift
                                RUNTIME_INTERVAL_SECS=$1
                                ;;
        --stop-after )          shift
                                STOP_AFTER_SECS=$1
                                ;;
        --help )                usage
                                exit
                                ;;
        * )                     usage
                                exit 1
    esac
    shift
done

# At least PID has to be provided
if [[ -z "$PROCESS_IDENTIFIER" ]]
then
      usage
      echo "Provide the correct process id:"
      jcmd
      exit 1
fi

HUMAN_READABLE_FORMAT='+%Y-%m-%d %H:%M:%S'
FILESYSTEM_READABLE_FORMAT='+%Y%m%d_%H%M%S'
OUTPUT_FOLDER="${OUTPUT_ROOT}/monitoring_session_started_$(date "$FILESYSTEM_READABLE_FORMAT")"
THREAD_DUMP_FOLDER="$OUTPUT_FOLDER/thread_dumps"
HEAP_DUMP_FOLDER="$OUTPUT_FOLDER/heap_dumps"
GC_STATS_FILE="$OUTPUT_FOLDER/gc_stats.csv"
RUNTIME_STATS_FILE="$OUTPUT_FOLDER/runtime_stats.csv"
JVM_INFO_FILE="$OUTPUT_FOLDER/jvm_info.txt"

# Preparing output folders
echo "Data will be saved to $OUTPUT_FOLDER"
mkdir -p ${OUTPUT_FOLDER}
mkdir -p ${THREAD_DUMP_FOLDER}
mkdir -p ${HEAP_DUMP_FOLDER}

# Dumping JVM INFO
echo "JVM version $(jcmd ${PROCESS_IDENTIFIER} VM.version)" > ${JVM_INFO_FILE}
echo "" >> ${JVM_INFO_FILE}
echo "JVM uptime $(jcmd ${PROCESS_IDENTIFIER} VM.uptime)" >> ${JVM_INFO_FILE}
echo "" >> ${JVM_INFO_FILE}
echo "JVM command line $(jcmd ${PROCESS_IDENTIFIER} VM.command_line)" >> ${JVM_INFO_FILE}
echo "" >> ${JVM_INFO_FILE}

# Initializing files
echo "Date,CPU%,S0C,S1C,S0U,S1U,EC,EU,OC,OU,MC,MU,CCSC,CCSU,YGC,YGCT,FGC,FGCT,GCT" > ${RUNTIME_STATS_FILE}

START=$(date +"%s")

GC_LOGS_FILE=$(jcmd ${PROCESS_IDENTIFIER} VM.command_line | grep jvm_args | sed 's/^.*Xlog[:]*gc:\([^\s]*\) .*$/\1/')

if [[ -z "$GC_LOGS_FILE" ]]; then
  echo "GC logs file was not found, did you set jvm args for GC logs?: for JDK 9+ '-Xlog:gc:/path/to/gc.log', JDK 8 '-Xloggc:/path/to/gc.log -XX:+PrintGC -XX:+PrintGCTimeStamps -XX:+PrintGCDateStamps'"
else
  echo "Found GC logs file: $GC_LOGS_FILE"
fi

LAST_GC=0
LAST_CPU=0
LAST_RUNTIME=0
LAST_HEAP=${START}
LAST_THREAD=${START}

function tick() {
  NOW=$(date +"%s")
  NOW_HUMAN=$(date "${HUMAN_READABLE_FORMAT}")
  NOW_FILESYSTEM=$(date "${FILESYSTEM_READABLE_FORMAT}")

  # We can set a stop after a number of seconds
  if [[ "$STOP_AFTER_SECS" -gt "0" ]] && [[ "$NOW" -gt $(( $START + $STOP_AFTER_SECS )) ]]; then
    echo "Maximum running time reached: job completed successfully."
    exit 0
  fi

  # Runtime live
  if [[ "$NOW" -ge $(( $LAST_RUNTIME + $RUNTIME_INTERVAL_SECS )) ]]; then
    CPU_USAGE=$(ps -p ${PROCESS_IDENTIFIER} -o %cpu | grep '\.')
    MEMORY_USAGE=$(jstat -gc ${PROCESS_IDENTIFIER} | grep '\.' | sed -E 's/ +/,/g')
    echo "$NOW_HUMAN,$CPU_USAGE,$MEMORY_USAGE" >> ${RUNTIME_STATS_FILE}
    LAST_RUNTIME=${NOW}
  fi

  # GC logs
  if [[ "$NOW" -ge $(( $LAST_GC + $GC_INTERVAL_SECS )) ]] && [[ ! -z "$GC_LOGS_FILE" ]]; then
    cp ${GC_LOGS_FILE} ${GC_STATS_FILE}
    LAST_GC=${NOW}
  fi

  # Thread dump
  if [[ "$NOW" -ge $(( $LAST_THREAD + $THREAD_DUMP_INTERVAL_SECS )) ]]; then
    THREAD_FILE="${THREAD_DUMP_FOLDER}/thread-dump-${NOW_FILESYSTEM}"
    jcmd ${PROCESS_IDENTIFIER} Thread.print > ${THREAD_FILE}
    LAST_THREAD=${NOW}
    echo "${NOW_HUMAN} - Thread dump saved to: $THREAD_FILE"
  fi

  # Heap dump
  if [[ "$NOW" -ge $(( $LAST_HEAP + $HEAP_DUMP_INTERVAL_SECS )) ]]; then
    HEAP_FILE="${HEAP_DUMP_FOLDER}/heap-dump-${NOW_FILESYSTEM}.hprof"
    jcmd ${PROCESS_IDENTIFIER} GC.heap_dump ${HEAP_FILE}
    LAST_HEAP=${NOW}
    echo "${NOW_HUMAN} - Heap dump saved to: $HEAP_FILE"
  fi
}

while sleep 1; do tick; done
