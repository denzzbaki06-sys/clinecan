#!/usr/bin/env python3
"""One local end-to-end execution. Default: use an existing server-side key; --demo: no paid calls.
Run after Maven verify and trusted sandbox image preparation. Never prints credentials or raw logs.
"""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import time
import urllib.request

parser = argparse.ArgumentParser()
parser.add_argument('--demo', action='store_true', help='Force deterministic DEMO without paid calls')
args = parser.parse_args()
if not args.demo and not os.environ.get('CLINECAN_LLM_API_KEY', '').strip():
    print('SKIPPED: No server-side API key is present. Use --demo for a no-cost sandbox smoke test.')
    raise SystemExit(0)
root = Path(__file__).resolve().parent.parent
jar = root / 'clinecan-backend/target/backend-0.0.1-SNAPSHOT.jar'
java = str(Path(os.environ['JAVA_HOME']) / 'bin/java') if os.environ.get('JAVA_HOME') else shutil.which('java')
if not jar.exists() or not java:
    raise SystemExit('Build the backend with Java 21 and Maven verify first.')
env = dict(os.environ)
if args.demo:
    env['CLINECAN_LLM_API_KEY'] = ''
env['CLINECAN_SANDBOX_ENABLED'] = 'true'
env['CLINECAN_REPAIR_MAX_ATTEMPTS'] = '0'  # One bounded execution; no additional repair calls.
port = 18082
base = f'http://127.0.0.1:{port}/api/agent'
# Refuse to call a pre-existing process on the smoke port.
import socket
with socket.socket() as probe:
    if probe.connect_ex(('127.0.0.1', port)) == 0:
        raise SystemExit('Smoke-test port 18082 is occupied. Stop that test server first.')
process = subprocess.Popen([java, '-jar', str(jar), f'--server.port={port}', '--server.address=127.0.0.1'],
                           env=env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
try:
    for _ in range(100):
        if process.poll() is not None:
            raise RuntimeError('Backend did not start; verify Java 21 and configuration.')
        try:
            with urllib.request.urlopen(base + '/capabilities', timeout=1) as response:
                mode = json.load(response)['generationMode']
            break
        except (OSError, ValueError):
            time.sleep(.1)
    else:
        raise RuntimeError('Backend startup timed out.')
    expected = 'DEMO' if args.demo else 'LLM'
    if mode != expected:
        raise RuntimeError('Unexpected generation mode; no request sent.')
    payload = json.dumps({'prompt': 'Create a minimal todo app with three sample tasks.'}).encode()
    request = urllib.request.Request(base + '/executions', data=payload, headers={'Content-Type': 'application/json'})
    with urllib.request.urlopen(request, timeout=10) as response:
        execution_id = json.load(response)['id']
    deadline = time.monotonic() + 300
    while time.monotonic() < deadline:
        with urllib.request.urlopen(base + '/executions/' + execution_id, timeout=5) as response:
            result = json.load(response)
        if result['status'] in ('COMPLETED', 'FAILED', 'CANCELLED', 'TIMED_OUT'):
            print(json.dumps({'mode': mode, 'state': result['status'], 'buildSuccess': bool(result.get('build') and result['build']['success']),
                              'artifactBytes': result['preview']['bytes'], 'repairAttempts': result['repairAttempts']}))
            raise SystemExit(0 if result['status'] == 'COMPLETED' and result['build']['success'] else 1)
        time.sleep(.5)
    raise RuntimeError('Smoke execution timed out.')
except (OSError, ValueError, RuntimeError):
    print('Smoke test could not complete. Check the local backend, Docker image and server-side configuration.')
    raise SystemExit(1)
finally:
    process.terminate()
    try:
        process.wait(timeout=20)
    except subprocess.TimeoutExpired:
        process.kill()
        process.wait()
