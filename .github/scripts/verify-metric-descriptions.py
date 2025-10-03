#!/usr/bin/env python3
"""
Verify that metric descriptions in Java code match the brief field in semantic conventions.
This script clones the semantic-conventions repo and compares metric descriptions.
"""

import os
import sys
import yaml
import subprocess
from pathlib import Path

# Metrics to check
METRICS_TO_CHECK = {
    # RPC metrics
    "rpc.server.duration": {
        "file": "instrumentation-api-incubator/src/main/java/io/opentelemetry/instrumentation/api/incubator/semconv/rpc/RpcServerMetrics.java",
        "yaml": "rpc/metrics.yaml",
        "metric_id": "metric.rpc.server.duration"
    },
    "rpc.client.duration": {
        "file": "instrumentation-api-incubator/src/main/java/io/opentelemetry/instrumentation/api/incubator/semconv/rpc/RpcClientMetrics.java",
        "yaml": "rpc/metrics.yaml",
        "metric_id": "metric.rpc.client.duration"
    },
    # HTTP metrics
    "http.server.request.duration": {
        "file": "instrumentation-api/src/main/java/io/opentelemetry/instrumentation/api/semconv/http/HttpServerMetrics.java",
        "yaml": "http/metrics.yaml",
        "metric_id": "metric.http.server.request.duration"
    },
    "http.client.request.duration": {
        "file": "instrumentation-api/src/main/java/io/opentelemetry/instrumentation/api/semconv/http/HttpClientMetrics.java",
        "yaml": "http/metrics.yaml",
        "metric_id": "metric.http.client.request.duration"
    },
    "http.server.active_requests": {
        "file": "instrumentation-api-incubator/src/main/java/io/opentelemetry/instrumentation/api/incubator/semconv/http/HttpServerExperimentalMetrics.java",
        "yaml": "http/metrics.yaml",
        "metric_id": "metric.http.server.active_requests"
    },
    "http.server.request.body.size": {
        "file": "instrumentation-api-incubator/src/main/java/io/opentelemetry/instrumentation/api/incubator/semconv/http/HttpServerExperimentalMetrics.java",
        "yaml": "http/metrics.yaml",
        "metric_id": "metric.http.server.request.body.size"
    },
    "http.server.response.body.size": {
        "file": "instrumentation-api-incubator/src/main/java/io/opentelemetry/instrumentation/api/incubator/semconv/http/HttpServerExperimentalMetrics.java",
        "yaml": "http/metrics.yaml",
        "metric_id": "metric.http.server.response.body.size"
    },
    "http.client.request.body.size": {
        "file": "instrumentation-api-incubator/src/main/java/io/opentelemetry/instrumentation/api/incubator/semconv/http/HttpClientExperimentalMetrics.java",
        "yaml": "http/metrics.yaml",
        "metric_id": "metric.http.client.request.body.size"
    },
    "http.client.response.body.size": {
        "file": "instrumentation-api-incubator/src/main/java/io/opentelemetry/instrumentation/api/incubator/semconv/http/HttpClientExperimentalMetrics.java",
        "yaml": "http/metrics.yaml",
        "metric_id": "metric.http.client.response.body.size"
    },
    # Database metrics
    "db.client.operation.duration": {
        "file": "instrumentation-api-incubator/src/main/java/io/opentelemetry/instrumentation/api/incubator/semconv/db/DbClientMetrics.java",
        "yaml": "database/metrics.yaml",
        "metric_id": "metric.db.client.operation.duration"
    },
    "db.client.connection.count": {
        "file": "instrumentation-api-incubator/src/main/java/io/opentelemetry/instrumentation/api/incubator/semconv/db/DbConnectionPoolMetrics.java",
        "yaml": "database/metrics.yaml",
        "metric_id": "metric.db.client.connection.count"
    },
    # GenAI metrics
    "gen_ai.client.token.usage": {
        "file": "instrumentation-api-incubator/src/main/java/io/opentelemetry/instrumentation/api/incubator/semconv/genai/GenAiClientMetrics.java",
        "yaml": "gen-ai/metrics.yaml",
        "metric_id": "metric.gen_ai.client.token.usage"
    },
    "gen_ai.client.operation.duration": {
        "file": "instrumentation-api-incubator/src/main/java/io/opentelemetry/instrumentation/api/incubator/semconv/genai/GenAiClientMetrics.java",
        "yaml": "gen-ai/metrics.yaml",
        "metric_id": "metric.gen_ai.client.operation.duration"
    },
}


def clone_semconv_repo(temp_dir):
    """Clone the semantic-conventions repository."""
    semconv_path = os.path.join(temp_dir, "semantic-conventions")
    if not os.path.exists(semconv_path):
        print("Cloning semantic-conventions repository...")
        subprocess.run(
            ["git", "clone", "--depth", "1", 
             "https://github.com/open-telemetry/semantic-conventions.git", 
             semconv_path],
            check=True
        )
    return semconv_path


def get_semconv_description(semconv_path, yaml_file, metric_id):
    """Get the brief description from semantic conventions YAML."""
    yaml_path = os.path.join(semconv_path, "model", yaml_file)
    
    if not os.path.exists(yaml_path):
        return None
    
    with open(yaml_path, 'r') as f:
        data = yaml.safe_load(f)
    
    if 'groups' not in data:
        return None
    
    for group in data['groups']:
        if group.get('id') == metric_id:
            return group.get('brief')
    
    return None


def extract_description_from_java(file_path, metric_name):
    """Extract the description from a Java metrics file."""
    if not os.path.exists(file_path):
        return None
    
    with open(file_path, 'r') as f:
        content = f.read()
    
    # Look for the metric builder pattern with setDescription
    import re
    # Match .histogramBuilder("metric.name") or .upDownCounterBuilder("metric.name") 
    # followed by .setDescription("description") - handle multiline strings
    # First, try to find where the metric name appears
    metric_pattern = rf'(?:histogram|counter|upDownCounter)Builder\([^)]*"{re.escape(metric_name)}"[^)]*\)'
    metric_match = re.search(metric_pattern, content, re.DOTALL)
    
    if metric_match:
        # Look for setDescription after the metric builder
        # Search from the metric builder position onwards
        remaining_content = content[metric_match.end():]
        
        # Match setDescription with either single-line or multi-line string
        desc_pattern = r'\.setDescription\(\s*"([^"]+)"\s*\)'
        desc_match = re.search(desc_pattern, remaining_content, re.DOTALL)
        
        if desc_match:
            return desc_match.group(1)
    
    return None


def main():
    repo_root = os.getcwd()
    temp_dir = "/tmp/semconv-check"
    os.makedirs(temp_dir, exist_ok=True)
    
    # Clone semantic conventions
    semconv_path = clone_semconv_repo(temp_dir)
    
    mismatches = []
    
    for metric_name, info in METRICS_TO_CHECK.items():
        java_file = os.path.join(repo_root, info["file"])
        
        # Get description from Java code
        java_desc = extract_description_from_java(java_file, metric_name)
        
        if java_desc is None:
            print(f"⚠️  {metric_name}: Could not extract description from Java code")
            continue
        
        # Get description from semantic conventions
        semconv_desc = get_semconv_description(semconv_path, info["yaml"], info["metric_id"])
        
        if semconv_desc is None:
            print(f"⚠️  {metric_name}: Not found in semantic conventions")
            continue
        
        # Compare
        if java_desc == semconv_desc:
            print(f"✅ {metric_name}: Match")
        else:
            print(f"❌ {metric_name}: MISMATCH")
            print(f"   Java:    '{java_desc}'")
            print(f"   Semconv: '{semconv_desc}'")
            mismatches.append({
                'metric': metric_name,
                'file': info['file'],
                'java': java_desc,
                'semconv': semconv_desc
            })
    
    if mismatches:
        print(f"\n❌ Found {len(mismatches)} mismatch(es)!")
        
        # Write mismatches to a file for the workflow to use
        with open(os.path.join(temp_dir, "mismatches.txt"), 'w') as f:
            for m in mismatches:
                f.write(f"Metric: {m['metric']}\n")
                f.write(f"File: {m['file']}\n")
                f.write(f"Java description: {m['java']}\n")
                f.write(f"Expected (semconv): {m['semconv']}\n")
                f.write("\n")
        
        return 1
    else:
        print("\n✅ All metric descriptions match semantic conventions!")
        return 0


if __name__ == "__main__":
    sys.exit(main())
