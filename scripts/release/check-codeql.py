"""Fail release verification on HIGH/CRITICAL CodeQL security findings in SARIF."""
from pathlib import Path
import json
import math
import sys

directory = Path(sys.argv[1] if len(sys.argv) > 1 else 'codeql-results')
files = sorted(directory.rglob('*.sarif'))
if not files:
    raise SystemExit('CodeQL SARIF output is missing; cannot evaluate the security gate.')
findings = []
for filename in files:
    document = json.loads(filename.read_text(encoding='utf-8'))
    if document.get('version') != '2.1.0' or not document.get('runs'):
        raise SystemExit('Invalid or empty CodeQL SARIF document; security gate cannot be evaluated.')
    for run in document.get('runs', []):
        driver = run.get('tool', {}).get('driver', {})
        rules = driver.get('rules', [])
        by_id = {rule['id']: rule for rule in rules}
        for result in run.get('results', []):
            rule = by_id.get(result.get('ruleId'), {})
            if not rule and 'ruleIndex' in result:
                rule = rules[result['ruleIndex']]
            properties = {**rule.get('properties', {}), **result.get('properties', {})}
            score = properties.get('security-severity')
            if score is not None:
                severity = float(score)
                if not math.isfinite(severity) or not 0 <= severity <= 10:
                    raise SystemExit('Invalid CodeQL security-severity value.')
                if severity >= 7:
                    findings.append(result.get('ruleId', rule.get('id', 'unknown rule')))
            elif result.get('level') == 'error' or 'security' in properties.get('tags', []):
                findings.append(result.get('ruleId', 'unclassified error'))
if findings:
    raise SystemExit('CodeQL security gate failed: ' + ', '.join(sorted(set(findings))))
print(f'CodeQL HIGH/CRITICAL security gate passed for {len(files)} SARIF result file(s).')
