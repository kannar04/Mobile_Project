"""Live notification delivery on both signed-in installations; exact synthetic fixtures only."""
from pathlib import Path
import json,subprocess,time
from importlib.machinery import SourceFileLoader

base=SourceFileLoader('two_device',str(Path(__file__).with_name('run-two-device.py'))).load_module()
node=base.ROOT/'build/media-node/node_modules/node/bin/node.exe'
fixture=base.ROOT/'firebase-tests/two-device-notifications.cjs'
test='com.gmail.manhle240406.project.adoption.TwoDeviceNotificationTest'
results=[]
def native(role,method):
    print('Notification test',role,method,flush=True)
    args=[str(base.ADB),'-s',base.DEVICES[role],'shell','am','instrument','-w','-r','-e','class',test+'#'+method,base.RUNNER]
    run=subprocess.run(args,capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=180)
    log=run.stdout+'\n'+run.stderr
    (base.OUT/('notifications-'+role+'-'+method+'.log')).write_text(log,encoding='utf-8')
    passed='OK (1 test)' in log and 'FAILURES!!!' not in log
    results.append({'role':role,'method':method,'passed':passed})
    (base.OUT/'notification-results.json').write_text(json.dumps(results,indent=2))
    if not passed:raise RuntimeError('Notification test failed: '+role+' '+method)

try:
    for role,serial in base.DEVICES.items():
        # Clears only this app's old OS notifications before creating fresh fixtures.
        subprocess.run([str(base.ADB),'-s',serial,'shell','am','force-stop','com.gmail.manhle240406.project'],check=True)
        api=int(subprocess.check_output([str(base.ADB),'-s',serial,'shell','getprop','ro.build.version.sdk'],text=True).strip())
        if api>=33:subprocess.run([str(base.ADB),'-s',serial,'shell','pm','grant','com.gmail.manhle240406.project','android.permission.POST_NOTIFICATIONS'],check=True)
        native(role,'bindInstallation')
    subprocess.run([str(node),str(fixture)],check=True)
    for role in base.DEVICES:native(role,'realNotificationsOpenInbox')
    print({'bothInstallationsNotificationChecksPassed':True},flush=True)
finally:
    if (base.OUT/'notification-fixture.json').exists():
        try:subprocess.run([str(node),str(fixture),'audit'],check=True)
        finally:subprocess.run([str(node),str(fixture),'cleanup'],check=True)
