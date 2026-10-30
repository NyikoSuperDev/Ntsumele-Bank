"""Exercise fail-closed SARIF gating without requiring a GitHub CodeQL run."""
from pathlib import Path
import json
import subprocess
import sys
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / 'check-codeql.py'

class CodeqlGateTest(unittest.TestCase):
    def execute(self, document):
        with tempfile.TemporaryDirectory() as temporary:
            if document is not None:
                (Path(temporary) / 'test.sarif').write_text(json.dumps(document), encoding='utf-8')
            return subprocess.run([sys.executable, str(SCRIPT), temporary], capture_output=True).returncode

    def report(self, score, results=True):
        rule = {'id': 'security-rule', 'properties': {'tags': ['security']}}
        if score is not None:
            rule['properties']['security-severity'] = score
        return {'version': '2.1.0', 'runs': [{'tool': {'driver': {'rules': [rule]}},
                'results': [{'ruleId': 'security-rule', 'level': 'warning'}] if results else []}]}

    def test_clean_and_lower_severity_results_pass(self):
        self.assertEqual(self.execute(self.report('9.0', results=False)), 0)
        self.assertEqual(self.execute(self.report('6.9')), 0)

    def test_high_security_results_block_release(self):
        self.assertNotEqual(self.execute(self.report('7.0')), 0)

    def test_missing_or_invalid_analysis_blocks_release(self):
        self.assertNotEqual(self.execute(None), 0)
        self.assertNotEqual(self.execute({'version': '2.1.0', 'runs': []}), 0)

    def test_unclassified_or_invalid_security_severity_blocks_release(self):
        self.assertNotEqual(self.execute(self.report(None)), 0)
        self.assertNotEqual(self.execute(self.report('NaN')), 0)

if __name__ == '__main__':
    unittest.main()
