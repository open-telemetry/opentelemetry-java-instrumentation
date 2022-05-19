#!/bin/bash

while true
do
  sleep 60
  file="/tmp/deadlock-detector-$(date +"%Y-%m-%d-%H-%M-%S").txt"
  ps aux >> $file
  free >> $file
  cat /proc/meminfo >> $file
  for pid in $(jps | grep -v Jps | awk '{ print $1 }')
  do
    jcmd $pid VM.command_line >> $file
    jcmd $pid Thread.print >> $file
    if jcmd $pid Thread.print | grep -q SimpleLogger
    then
      # check once more to eliminate most of the sporadic finds
      if jcmd $pid Thread.print | grep -q SimpleLogger
      then
        jcmd $pid GC.heap_dump /tmp/deadlock-detector-$pid.hprof
      fi
    fi
  done
done &
