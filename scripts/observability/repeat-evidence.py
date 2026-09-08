#!/usr/bin/env python3
"""Sequential fresh-container runs; stop on failure and preserve each raw report."""
import pathlib, subprocess, shutil, json, hashlib, datetime
root = pathlib.Path(__file__).resolve().parents[2]
out = root / 'docs/experiments/2026-09-08-portfolio-completion/load'
out.mkdir(parents=True, exist_ok=True)
for run in range(1, 4):
    target = out / f'run-{run}'
    target.mkdir(exist_ok=False)
    command = ['./gradlew', 'observabilityEvidenceTest', '-PevidenceStageSeconds=100', '--rerun-tasks', '--console=plain']
    with (target / 'execution.log').open('w') as log:
        result = subprocess.run(command, cwd=root, stdout=log, stderr=subprocess.STDOUT)
    if result.returncode:
        raise SystemExit(f'Run {run} failed; inspect local execution.log')
    source = root / 'build/reports/observability-evidence'
    for name in ['result.json', 'browser.json']:
        shutil.copyfile(source / name, target / name)
    (target / 'execution.log').unlink() # Full framework logs are local diagnostics, not public artifacts.
    print(f'Run {run}/3 passed', flush=True)
