import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
const root=fileURLToPath(new URL('../modules/evaluation-workbench/',import.meta.url));
const action=process.argv[2]||'help';
function run(args){const result=spawnSync(process.execPath,args,{cwd:root,stdio:'inherit'});if(result.error){console.error('작업대 실행에 실패했습니다. Node.js 22 이상과 모듈 경로를 확인하세요.');process.exit(1)}if(result.signal)process.exit(1);if(result.status!==0)process.exit(result.status||1)}
if(action==='check'){
  run(['--check','public/app.js']);
  run(['--check','public/automation.js']);
  run(['scripts/build.mjs']);
  run(['--test','tests/integration.test.mjs','tests/scoring.test.cjs']);
}else if(action==='start'){
  run(['scripts/build.mjs']);
  run(['scripts/dev.mjs']);
}else if(action==='help'){
  console.log('node scripts/workbench.mjs check | start');
}else{
  console.error('알 수 없는 명령입니다. check 또는 start를 사용하세요.');
  process.exit(64);
}
