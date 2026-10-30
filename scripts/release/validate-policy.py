"""Fail CI if immutable build inputs or protected release boundaries regress."""
from pathlib import Path
import json
import re
import xml.etree.ElementTree as ET

project = Path(__file__).resolve().parents[2]
errors = []
for workflow in sorted((project / '.github/workflows').glob('*.yml')):
    source = workflow.read_text(encoding='utf-8')
    for action in re.findall(r'^\s*(?:-\s*)?uses:\s*([^\s#]+)', source, re.M):
        if action.startswith('./'):
            continue
        if not re.fullmatch(r'[\w.-]+/[\w./-]+@[a-f0-9]{40}', action):
            errors.append(f'{workflow.name}: action is not pinned to a full commit: {action}')
    if 'pull_request_target:' in source or 'permissions: write-all' in source or 'continue-on-error: true' in source:
        errors.append(f'{workflow.name}: privileged PR trigger, broad permission or ignored failure')
    services_indent = None
    for line in source.splitlines():
        if not line.strip() or line.lstrip().startswith('#'):
            continue
        indent = len(line) - len(line.lstrip())
        if services_indent is not None and indent <= services_indent:
            services_indent = None
        if line.strip() == 'services:':
            services_indent = indent
        image_match = re.match(r'image:\s*(\S+)', line.strip())
        if services_indent is not None and image_match:
            image = image_match.group(1)
            if not re.fullmatch(r'[^@]+@sha256:[a-f0-9]{64}', image):
                errors.append(f'{workflow.name}: unpinned service image: {image}')
for image in re.findall(r'^FROM\s+(\S+)', (project / 'Dockerfile').read_text(), re.M):
    if not re.fullmatch(r'[^@]+@sha256:[a-f0-9]{64}', image):
        errors.append(f'Dockerfile: unpinned image: {image}')
if not re.match(r'# syntax=\S+@sha256:[a-f0-9]{64}\n', (project / 'Dockerfile').read_text()):
    errors.append('Dockerfile frontend must also be digest-pinned')
for image in re.findall(r'^\s*image:\s*(\S+)', (project / 'compose.yaml').read_text(), re.M):
    if not re.fullmatch(r'[^@]+@sha256:[a-f0-9]{64}', image):
        errors.append(f'compose.yaml: unpinned image: {image}')
ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
pom = ET.parse(project / 'backend/pom.xml')
profiles = {p.findtext('m:id', namespaces=ns): p for p in pom.findall('m:profiles/m:profile', ns)}
security = profiles['security']
plugins = security.findall('m:build/m:plugins/m:plugin', ns)
odc = next(p for p in plugins if p.findtext('m:artifactId', namespaces=ns) == 'dependency-check-maven')
assert odc.findtext('m:configuration/m:failBuildOnCVSS', namespaces=ns) == '7'
assert odc.findtext('m:configuration/m:failOnError', namespaces=ns) == 'true'
assert 'sbom' in profiles and 'postgres-tls' in profiles
json.loads((project / '.github/renovate.json').read_text())
release = (project / '.github/workflows/release.yml').read_text()
deploy = (project / '.github/workflows/deploy.yml').read_text()
assert 'needs: verify' in release and 'environment: release' in release
assert release.index('Gate publishing') < release.index('docker push') < release.index('cosign sign')
assert 'environment: production' in deploy and "github.ref == 'refs/heads/main'" in deploy
assert 'vars.DEPLOY_ENABLED' in deploy and 'vars.RELEASE_ENABLED' in release
if errors:
    raise SystemExit('\n'.join(errors))
print('Supply-chain pins, scan gates and protected release/deployment boundaries validated.')
