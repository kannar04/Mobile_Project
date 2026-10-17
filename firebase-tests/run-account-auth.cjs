// Uses the installed Firebase Auth emulator directly; no deployment, credentials or live project.
const {spawn}=require('node:child_process');
const {createApp}=require('firebase-tools/lib/emulator/auth/server');
(async()=>{
 const app=await createApp('demo-anilove-account-tests',0);
 const server=app.listen(9299,'127.0.0.1',()=>{
  const child=spawn(process.execPath,['--test','account-auth.test.mjs'],{stdio:'inherit'});
  child.on('exit',code=>server.close(()=>process.exit(code??1)));
  child.on('error',()=>server.close(()=>process.exit(1)));
 });
 server.on('error',()=>process.exit(1));
})().catch(()=>process.exit(1));
