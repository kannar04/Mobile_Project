"""Host orchestration for the explicitly isolated two-device Android acceptance run.

Preserves existing Firebase identities. Stops on the first failing native step.
No provider credential or control secret is printed or sent as an adb argument.
"""
from pathlib import Path
import argparse,json,os,subprocess,sys,time,urllib.request

ROOT=Path(__file__).resolve().parent.parent
OUT=ROOT/'build/two-device'
ADB=Path(os.environ['LOCALAPPDATA'])/'Android/Sdk/platform-tools/adb.exe'
DEVICES={'host':'TSL7IRUGNFCEZ9XS','adopter':'localhost:5555'}
RUNNER='com.gmail.manhle240406.project.test/androidx.test.runner.AndroidJUnitRunner'
TEST='com.gmail.manhle240406.project.adoption.TwoDeviceLifecycleTest'

def control(body):
    cfg=json.loads((Path(os.environ['USERPROFILE'])/'.anilove/two-device-run.json').read_text(encoding='utf-8'))
    req=urllib.request.Request(f"http://127.0.0.1:{cfg['port']}/control",data=json.dumps(body).encode(),headers={'Content-Type':'application/json','X-Fixture-Control':cfg['controlSecret']})
    with urllib.request.urlopen(req,timeout=120) as response:return json.load(response)['result']

def native(scenario,role,action,report=None,suffix=None):
    name='-'.join(x for x in [scenario,role,action,report,suffix] if x)
    completed=json.loads((OUT/'native-results.json').read_text()) if (OUT/'native-results.json').exists() else []
    if any(x['step']==name and x['passed'] for x in completed):return
    args=[str(ADB),'-s',DEVICES[role],'shell','am','instrument','-w','-r','-e','class',TEST,'-e','scenario',scenario,'-e','role',role,'-e','action',action]
    if report:args+=['-e','report',report]
    args.append(RUNNER)
    print('Testing',name,flush=True)
    start=time.monotonic()
    try:
        result=subprocess.run(args,capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=180)
        log=result.stdout+'\n'+result.stderr
        passed='OK (1 test)' in log and 'FAILURES!!!' not in log and 'INSTRUMENTATION_FAILED' not in log
    except subprocess.TimeoutExpired as error:
        log=str(error);passed=False
    (OUT/(name+'.log')).write_text(log,encoding='utf-8')
    completed.append({'step':name,'passed':passed,'seconds':round(time.monotonic()-start,2)})
    temp=OUT/'native-results.tmp';temp.write_text(json.dumps(completed,indent=2));temp.replace(OUT/'native-results.json')
    if not passed:raise RuntimeError('Native step failed: '+name+'; inspect its scoped log')

def start_case(name):
    native(name,'host','prepare');native(name,'adopter','apply')
    state=control({'kind':'state'})
    if state['cases'][name].get('adoptionId'):
        native(name,'host','verifySelection')
    else:
        native(name,'host','select')

def agreement_handover(name,verify=True):
    native(name,'host','accept');native(name,'adopter','accept');native(name,'host','baseline');native(name,'adopter','ack');native(name,'host','ready');native(name,'adopter','receive')
    if verify:native(name,'host','verify')

def run():
    parser=argparse.ArgumentParser();parser.add_argument('command',choices=['init','run','audit','cleanup']);args=parser.parse_args();OUT.mkdir(parents=True,exist_ok=True)
    if args.command!='run':
        result=control({'kind':args.command});(OUT/(args.command+'-result.json')).write_text(json.dumps(result,indent=2));print({'command':args.command,'completed':True});return
    for serial in DEVICES.values():subprocess.run([str(ADB),'-s',serial,'reverse','tcp:8765','tcp:8765'],check=True,capture_output=True)
    start_case('success')
    state=control({'kind':'state'})
    if not (OUT/'failure-injection-armed.json').exists():
        control({'kind':'failAfterCommit','role':'host','command':'acceptContract'});(OUT/'failure-injection-armed.json').write_text('{"armed":true}')
    agreement_handover('success')
    for offset in [3,6,9,16,23,30,44,58,61,90]:
        report=f'D{offset:02}'
        control({'kind':'advance','case':'success','reportId':report})
        native('success','adopter','report',report);native('success','host','review',report)
        if offset==3:
            native('success','adopter','correct',report);native('success','host','review',report,suffix='correction')
    native('success','host','finalize');native('success','host','readonly');native('success','adopter','readonly')
    start_case('cancel');native('cancel','adopter','cancel');native('cancel','host','readonly')
    start_case('dispute');agreement_handover('dispute',verify=False);native('dispute','adopter','dispute');native('dispute','host','blockedVerify')
    control({'kind':'reviewer','case':'dispute'});native('dispute','host','cancel');native('dispute','adopter','readonly')
    audit=control({'kind':'audit'});(OUT/'audit-result.json').write_text(json.dumps(audit,indent=2));assert {x['name']:x['status'] for x in audit['results']}=={'success':'FINALIZED','cancel':'CANCELLED_BEFORE_HANDOVER','dispute':'CANCELLED_BEFORE_HANDOVER'}
    print({'two_device_run_passed':True,'production_rollout_unchanged':True},flush=True)

if __name__=='__main__':
    try:run()
    except Exception as error:print(str(error),file=sys.stderr);sys.exit(1)
