#!/usr/bin/env python3
"""Derive review-sized values from all preserved load reports without changing them."""
import json, pathlib, statistics, math
root = pathlib.Path(__file__).resolve().parents[2]
folder = root/'docs/experiments/2026-09-08-portfolio-completion'
reports = [json.loads((folder/f'load/run-{i}/result.json').read_text()) for i in range(1,4)]
assert len({r['compiledClassesSha256'] for r in reports}) == 1, 'Run bytecode changed'
assert len({json.dumps(r['fixture'],sort_keys=True) for r in reports}) == 1, 'Fixture changed'
assert len({json.dumps(r['environment'],sort_keys=True) for r in reports}) == 1, 'Runtime configuration changed'
summary={'scope':'Three fresh local environments; stepped 20/40/80 RPS, 100s each. Host resources/background processes not pinned.', 'compiledClassesSha256':reports[0]['compiledClassesSha256'],'runs':[]}
for i,r in enumerate(reports,1):
    stages=[]
    for s in r['stages']:
        stages.append({'rps':s['targetRps'],'requests':s['http']['requests'],'errors':s['http']['errors'],
                       'actualRps':s['http']['requestsPerSecond'],'httpP95Ms':s['http']['p95Ms'],
                       'commitP95Ms':s['admittedToCommit']['p95Ms'],'commitP99Ms':s['admittedToCommit']['p99Ms'],
                       'unmeasured':s['admittedToCommit']['unmeasured']})
    q=r['queryComparison'];b=statistics.median(q['baselineRawMs']);a=statistics.median(q['candidateRawMs'])
    resources=r['resourceSamples']
    summary['runs'].append({'run':i,'stages':stages,'recovery':{k:v for k,v in r['recoveryAdmittedToCommit'].items() if k!='rawMs'},
        'duplicate':{k:v for k,v in r['duplicateAdmittedToCommit'].items() if k!='rawMs'},'db':r['recovery'],
        'query':{'baselineMedianMs':b,'candidateMedianMs':a,'medianReductionPercent':100*(1-a/b)},
        'resources':{'maxHeapUsedBytes':max(s['heapUsedBytes'] for s in resources),'maxSampledReadyMessages':max(s['readyMessages'] for s in resources),
                     'maxSampledPublisherQueue':max(s['gps.publisher.queued'] or 0 for s in resources)}})
summary['normalRequests']=sum(s['requests'] for r in summary['runs'] for s in r['stages'])
summary['normalErrors']=sum(s['errors'] for r in summary['runs'] for s in r['stages'])
(folder/'load-summary.json').write_text(json.dumps(summary,indent=2)+'\n')
print(json.dumps({k:summary[k] for k in ['normalRequests','normalErrors']},indent=2))
