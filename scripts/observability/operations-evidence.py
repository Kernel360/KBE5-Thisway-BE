#!/usr/bin/env python3
"""Disposable real Prometheus/Alertmanager/Loki pipeline; every published port is loopback."""
import base64, datetime, hashlib, json, pathlib, re, secrets, subprocess, tempfile, time, urllib.request, urllib.error
ROOT = pathlib.Path(__file__).resolve().parents[2]
CFG = ROOT / 'infra/observability/operations'
OUT = ROOT / 'docs/experiments/2026-09-08-portfolio-completion/operations'
OUT.mkdir(parents=True, exist_ok=True)
NAME = 'thisway-evidence-' + secrets.token_hex(4)
containers = []
def docker(*args):
    return subprocess.check_output(['docker', *map(str,args)], text=True).strip()
def request(url, method='GET', body=None, credential=None):
    headers = {'Content-Type':'application/json'}
    if credential: headers['Authorization'] = 'Basic ' + base64.b64encode(credential.encode()).decode()
    req = urllib.request.Request(url, data=None if body is None else json.dumps(body).encode(), headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=10) as r: return r.status, r.read().decode()
    except urllib.error.HTTPError as e: return e.code, e.read().decode()
def wait(check, seconds=90):
    deadline = time.monotonic()+seconds
    while time.monotonic()<deadline:
        try:
            value = check()
            if value: return value
        except (OSError, ValueError, IndexError): pass
        time.sleep(1)
    raise AssertionError('Bounded wait expired')
def run(alias, image, mounts=(), port=None, command=()):
    args=['run','-d','--name',NAME+'-'+alias,'--network',NAME,'--network-alias',alias]
    for source,target in mounts: args += ['-v',f'{source}:{target}:ro']
    if port: args += ['-p',f'127.0.0.1::{port}']
    args += [image,*command]
    ident=docker(*args); containers.append(ident)
    if port: return 'http://'+docker('port',ident,str(port)+'/tcp').splitlines()[0]
    return ident
try:
    docker('network','create',NAME)
    with tempfile.TemporaryDirectory(prefix='thisway-log-keys-') as directory:
        secret_dir=pathlib.Path(directory)
        creds={}
        for role in ['reader','writer']:
            key=secrets.token_hex(32); creds[role]=role+':'+key
            digest=subprocess.check_output(['openssl','passwd','-apr1','-stdin'],input=key+'\n',text=True).strip()
            (secret_dir/role).write_text(role+':'+digest+'\n'); (secret_dir/role).chmod(0o644)
        receiver=run('receiver','python:3.12-alpine',[(ROOT/'scripts/observability/local-receiver.py','/app/receiver.py')],8080,['python','/app/receiver.py'])
        wait(lambda: request(receiver+'/events')[0]==200)
        run('alertmanager','prom/alertmanager:v0.28.1',[(CFG/'alertmanager.yml','/etc/alertmanager/alertmanager.yml')],command=['--config.file=/etc/alertmanager/alertmanager.yml'])
        prom=run('prometheus','prom/prometheus:v3.4.1',[(CFG/'prometheus.yml','/etc/prometheus/prometheus.yml'),(CFG/'rules.yml','/etc/prometheus/rules.yml')],9090)
        wait(lambda: request(prom+'/-/ready')[0]==200)
        wait(lambda: '1' == json.loads(request(prom+'/api/v1/query?query=up')[1])['data']['result'][0]['value'][1])
        began=datetime.datetime.now(datetime.timezone.utc).isoformat()
        assert request(receiver+'/fault/on','POST',{})[0]==200
        firing=wait(lambda: next((e for e in json.loads(request(receiver+'/events')[1]) if e['status']=='firing'),None))
        assert request(receiver+'/fault/off','POST',{})[0]==200
        resolved=wait(lambda: next((e for e in json.loads(request(receiver+'/events')[1]) if e['status']=='resolved'),None))
        loki=run('loki','grafana/loki:3.5.0',[(CFG/'loki.yml','/etc/loki/config.yml')],command=['-config.file=/etc/loki/config.yml'])
        gateway=run('gateway','nginx:1.28.0-alpine',[(CFG/'gateway.conf','/etc/nginx/nginx.conf'),(secret_dir/'reader','/run/secrets/reader'),(secret_dir/'writer','/run/secrets/writer')],8080)
        source=ROOT/'build/reports/observability-evidence/http-completion.jsonl'
        records=[json.loads(line) for line in source.read_text().splitlines()]
        assert records
        # Only fixed GPS completion events are accepted by this bounded evidence collector.
        pattern=re.compile(r'event=http_dispatch method=POST route=/api/logs/gps status=200 durationMs=[0-9.]+ asyncStarted=false failed=false')
        for record in records:
            assert set(record)=={'traceId','message'}
            assert re.fullmatch('[0-9a-f]{32}',record['traceId']) and pattern.fullmatch(record['message'])
        now=time.time_ns()
        payload={'streams':[{'stream':{'app':'thisway','event':'http_dispatch'},'values':[[str(now+i),json.dumps(r)] for i,r in enumerate(records)]}]}
        push=gateway+'/loki/api/v1/push'
        wait(lambda: request(push,'POST',payload,creds['writer'])[0]==204)
        import urllib.parse
        query='{app="thisway"} |= "'+records[0]['traceId']+'"'
        url=gateway+'/loki/api/v1/query_range?'+urllib.parse.urlencode({'query':query,'start':now-1000000000,'end':now+1000000000})
        found=wait(lambda: (lambda r: r if r[0]==200 and json.loads(r[1]).get('data',{}).get('result') else None)(request(url,credential=creds['reader'])))
        assert request(url)[0]==401
        assert request(url,credential=creds['writer'])[0]==401
        assert request(push,'POST',payload,creds['reader'])[0]==401
        assert request(gateway+'/config',credential=creds['reader'])[0]==403
        old={'streams':[{'stream':{'app':'thisway','event':'http_dispatch'},'values':[[str(now-25*3600*10**9),json.dumps(records[0])]]}]}
        assert request(push,'POST',old,creds['writer'])[0]==400
        # Effective retention configuration, not a claim that 24 hours elapsed.
        effective=docker('exec',loki,'/usr/bin/loki','-config.file=/etc/loki/config.yml','-verify-config=true')
        result={'measuredAt':began,'alertFiring':firing,'alertResolved':resolved,
                'correlationQuery':json.loads(found[1]),'ingestedRealHttpCompletions':len(records),
                'permissions':{'anonymousRead':401,'writerRead':401,'readerWrite':401,'configRead':403},
                'retention':{'configuredHours':24,'25HourOldIngestion':400,'configVerification':'passed','physicalDeletionAfter24Hours':'not observed'},
                'scope':'Disposable local services, synthetic fault exporter, actual Spring HTTP completion logs. No external recipient or production deployment.'}
        (OUT/'result.json').write_text(json.dumps(result,indent=2)+'\n')
        print('Alert firing/resolved delivery, correlation search, read/write denial and retention policy passed',flush=True)
finally:
    for ident in reversed(containers): subprocess.run(['docker','rm','-f',ident],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    subprocess.run(['docker','network','rm',NAME],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
