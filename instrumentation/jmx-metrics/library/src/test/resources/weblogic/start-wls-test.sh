#!/bin/bash
# Copyright The OpenTelemetry Authors
# SPDX-License-Identifier: Apache-2.0

set -e

export DOMAIN_HOME=/u01/oracle/user_projects/domains/$DOMAIN_NAME
PROPERTIES_FILE=/u01/oracle/properties/domain.properties
USER=$(awk '{print $1}' $PROPERTIES_FILE | grep username | cut -d '=' -f2)
PASS=$(awk '{print $1}' $PROPERTIES_FILE | grep password | cut -d '=' -f2)

JAVA_TOOL_OPTIONS='' _JAVA_OPTIONS='' wlst.sh -skipWLSModuleScanning \
  -loadProperties $PROPERTIES_FILE /u01/oracle/create-wls-domain.py

mkdir -p ${DOMAIN_HOME}/servers/${ADMIN_NAME}/security/
mkdir -p ${DOMAIN_HOME}/autodeploy/
cp /u01/oracle/testapp.war ${DOMAIN_HOME}/autodeploy/testapp.war
chmod -R g+w ${DOMAIN_HOME}
echo "username=${USER}" >> ${DOMAIN_HOME}/servers/${ADMIN_NAME}/security/boot.properties
echo "password=${PASS}" >> ${DOMAIN_HOME}/servers/${ADMIN_NAME}/security/boot.properties

# WLS 14.1.1 ships derby.jar + derbytools.jar; WLS 14.1.2+ also requires derbyshared.jar
DERBY_LIB=/u01/oracle/wlserver/common/derby/lib
PRE_CLASSPATH="${DERBY_LIB}/derby.jar:${DERBY_LIB}/derbytools.jar"
if [ -f "${DERBY_LIB}/derbyshared.jar" ]; then
  PRE_CLASSPATH="${PRE_CLASSPATH}:${DERBY_LIB}/derbyshared.jar"
fi
export PRE_CLASSPATH
export JAVA_OPTIONS="${JAVA_OPTIONS} ${WLS_AGENT_OPTS}"
exec ${DOMAIN_HOME}/bin/startWebLogic.sh
