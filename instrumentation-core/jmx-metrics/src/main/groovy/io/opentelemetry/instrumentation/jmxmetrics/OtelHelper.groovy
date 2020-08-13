/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.instrumentation.jmxmetrics

import io.opentelemetry.metrics.DoubleCounter
import io.opentelemetry.metrics.DoubleUpDownCounter
import io.opentelemetry.metrics.DoubleValueRecorder
import io.opentelemetry.metrics.LongCounter
import io.opentelemetry.metrics.LongUpDownCounter
import io.opentelemetry.metrics.LongValueRecorder

import javax.management.MBeanServerConnection
import javax.management.ObjectName

class OtelHelper {

    private final JmxClient jmxClient
    private final GroovyUtils groovyUtils

    final String scalar = '1'

    OtelHelper(JmxClient jmxClient, GroovyUtils groovyUtils) {
        this.jmxClient = jmxClient
        this.groovyUtils = groovyUtils
    }

    /**
     * Returns a list of {@link GroovyMBean} for a given object name String.
     * @param objNameStr - the {@link String} representation of an object name or pattern, to be
     * used as the argument to the basic {@link ObjectName} constructor for the JmxClient query.
     * @return a {@link List<GroovyMBean>} from which to create metrics.
     */
    List<GroovyMBean> queryJmx(String objNameStr) {
        return queryJmx(new ObjectName(objNameStr))
    }

    /**
     * Returns a list of {@link GroovyMBean} for a given {@link ObjectName}.
     * @param objName - the {@link ObjectName} used for the JmxClient query.
     * @return a {@link List<GroovyMBean>} from which to create metrics.
     */
    List<GroovyMBean> queryJmx(ObjectName objName) {
        Set<ObjectName> names = jmxClient.query(objName)
        MBeanServerConnection server = jmxClient.connection
        return names.collect { new GroovyMBean(server, it) }
    }

    DoubleCounter doubleCounter(String name, String description, String unit, Map<String, String> labels) {
        return groovyUtils.getDoubleCounter(name, description, unit, labels)
    }

    DoubleCounter doubleCounter(String name, String description, String unit) {
        return doubleCounter(name, description, unit, null)
    }

    DoubleCounter doubleCounter(String name, String description) {
        return doubleCounter(name, description, scalar)
    }

    DoubleCounter doubleCounter(String name) {
        return doubleCounter(name, '')
    }

    LongCounter longCounter(String name, String description, String unit, Map<String, String> labels) {
        return groovyUtils.getLongCounter(name, description, unit, labels)
    }

    LongCounter longCounter(String name, String description, String unit) {
        return longCounter(name, description, unit, null)
    }

    LongCounter longCounter(String name, String description) {
        return longCounter(name, description, scalar)
    }

    LongCounter longCounter(String name) {
        return longCounter(name, '')
    }

    DoubleUpDownCounter doubleUpDownCounter(String name, String description, String unit, Map<String, String> labels) {
        return groovyUtils.getDoubleUpDownCounter(name, description, unit, labels)
    }

    DoubleUpDownCounter doubleUpDownCounter(String name, String description, String unit) {
        return doubleUpDownCounter(name, description, unit, null)
    }

    DoubleUpDownCounter doubleUpDownCounter(String name, String description) {
        return doubleUpDownCounter(name, description, scalar)
    }

    DoubleUpDownCounter doubleUpDownCounter(String name) {
        return doubleUpDownCounter(name, '')
    }

    LongUpDownCounter longUpDownCounter(String name, String description, String unit, Map<String, String> labels) {
        return groovyUtils.getLongUpDownCounter(name, description, unit, labels)
    }

    LongUpDownCounter longUpDownCounter(String name, String description, String unit) {
        return longUpDownCounter(name, description, unit, null)
    }

    LongUpDownCounter longUpDownCounter(String name, String description) {
        return longUpDownCounter(name, description, scalar)
    }

    LongUpDownCounter longUpDownCounter(String name) {
        return longUpDownCounter(name, '')
    }

    DoubleValueRecorder doubleValueRecorder(String name, String description, String unit, Map<String, String> labels) {
        return groovyUtils.getDoubleValueRecorder(name, description, unit, labels)
    }

    DoubleValueRecorder doubleValueRecorder(String name, String description, String unit) {
        return doubleValueRecorder(name, description, unit, null)
    }

    DoubleValueRecorder doubleValueRecorder(String name, String description) {
        return doubleValueRecorder(name, description, scalar)
    }

    DoubleValueRecorder doubleValueRecorder(String name) {
        return doubleValueRecorder(name, '')
    }

    LongValueRecorder longValueRecorder(String name, String description, String unit, Map<String, String> labels) {
        return groovyUtils.getLongValueRecorder(name, description, unit, labels)
    }

    LongValueRecorder longValueRecorder(String name, String description, String unit) {
        return longValueRecorder(name, description, unit, null)
    }

    LongValueRecorder longValueRecorder(String name, String description) {
        return longValueRecorder(name, description, scalar)
    }

    LongValueRecorder longValueRecorder(String name) {
        return longValueRecorder(name, '')
    }

}
