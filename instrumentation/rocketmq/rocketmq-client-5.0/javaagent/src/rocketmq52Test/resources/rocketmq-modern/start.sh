#!/bin/bash
# Copyright The OpenTelemetry Authors
# SPDX-License-Identifier: Apache-2.0

set -euo pipefail

trap 'exit 0' TERM INT

sh bin/mqnamesrv &
sh bin/mqbroker -c /tmp/broker.properties &

(
  # Cluster-mode Proxy creates its heartbeat topic during startup.
  until timeout 10 java \
    -Xms32m -Xmx128m -XX:ActiveProcessorCount=2 \
    -Drmq.logback.configurationFile=/app/rocketmq/conf/rmq.tools.logback.xml \
    -cp 'conf:lib/*' \
    org.apache.rocketmq.tools.command.MQAdminStartup \
    clusterList -n "$NAMESRV_ADDR" | grep -F 'broker-a'; do
    sleep 1
  done

  exec sh bin/mqproxy -pm cluster -pc /tmp/proxy.json
) &

# Any component exiting makes this disposable test environment unusable.
wait -n
exit 1
