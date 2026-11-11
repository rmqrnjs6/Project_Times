export const PROVIDERS = [
 {id:'deepseek',name:'DeepSeek Flash',model:'deepseek-flash',key:'DEEPSEEK_API_KEY',endpoint:'https://api.deepseek.com/chat/completions'},
 {id:'qwen',name:'Qwen3.5-Plus',model:'qwen3.5-plus',key:'QWEN_API_KEY'},
 {id:'gemini',name:'Gemini 3.8 Flash',model:'gemini-3.8-flash',key:'GEMINI_API_KEY',endpoint:'https://generativelanguage.googleapis.com/v1beta/openai/chat/completions'}
];
const SYSTEM='수학 문제를 독립적으로 풀어 주세요. 필요한 가정과 정의역, 검토 가능한 핵심 계산 단계, 최종 답을 한국어로 간결하게 제시하세요. 불확실하거나 풀지 못한 부분은 명시하세요. 도구를 실제 실행하지 않았는데 실행 또는 형식 검증했다고 주장하지 마세요.';
export function qwenEndpoint(base){
 try{const u=new URL(base);if(u.protocol!=='https:'||u.username||u.password||u.port||u.search||u.hash)return null;
 const hostOK=/^[a-z0-9-]+\.(ap-southeast-1|us-east-1|cn-beijing|cn-hongkong|ap-northeast-1)\.maas\.aliyuncs\.com$/.test(u.hostname)||['dashscope-intl.aliyuncs.com','dashscope.aliyuncs.com','cn-hongkong.dashscope.aliyuncs.com'].includes(u.hostname);
 if(!hostOK||!/^\/compatible-mode\/v1\/?$/.test(u.pathname))return null;return u.origin+'/compatible-mode/v1/chat/completions';
 }catch{return null}
}
function isOwner(request,env){return !!(env.MODEL_OWNER_EMAIL&&request.headers.get('oai-authenticated-user-id')&&request.headers.get('oai-authenticated-user-email')?.toLowerCase()===env.MODEL_OWNER_EMAIL.toLowerCase());}
function configuration(p,env){const endpoint=p.id==='qwen'?qwenEndpoint(env.QWEN_BASE_URL):p.endpoint;return {endpoint,ready:!!(typeof env[p.key]==='string'&&env[p.key].trim()&&endpoint),reason:!env[p.key]?'key_missing':!endpoint?'endpoint_missing':'configured'};}
function json(data,status=200){return new Response(JSON.stringify(data),{status,headers:{'content-type':'application/json; charset=utf-8','cache-control':'no-store','x-content-type-options':'nosniff'}});}
function usageView(u){if(!u||typeof u!=='object')return null;const n=v=>Number.isFinite(v)&&v>=0?v:null;return {input_tokens:n(u.prompt_tokens),output_tokens:n(u.completion_tokens),total_tokens:n(u.total_tokens)};}
export function parseCompletion(payload){const choice=payload?.choices?.[0];const content=choice?.message?.content;if(typeof content!=='string'||!content.trim())return {status:'empty',text:'응답 본문이 없거나 추론 중 출력 한도에 도달했습니다.',usage:usageView(payload?.usage)};return {status:choice.finish_reason==='length'?'truncated':'succeeded',text:content.slice(0,20000),finish_reason:choice.finish_reason??null,usage:usageView(payload.usage)};}
export function parseStream(text){let content='',finish=null,usage=null;for(const line of text.split(/\r?\n/)){if(!line.startsWith('data:'))continue;const data=line.slice(5).trim();if(!data||data==='[DONE]')continue;const p=JSON.parse(data);if(p.error)throw new Error('provider_response_error');if(p.usage)usage=p.usage;const c=p.choices?.[0];if(c?.delta?.content)content+=c.delta.content;if(c?.finish_reason)finish=c.finish_reason;}return parseCompletion({choices:[{message:{content},finish_reason:finish}],usage});}
async function runProvider(provider,prompt,env,upstreamFetch){const config=configuration(provider,env);if(!config.ready)return {id:provider.id,model:provider.model,status:'not_configured',text:'API 키 또는 지역별 연결 주소를 등록해야 합니다.'};const body={model:provider.model,messages:[{role:'system',content:SYSTEM},{role:'user',content:prompt}],max_tokens:1024,stream:false};
 if(provider.id==='qwen')Object.assign(body,{enable_thinking:true,thinking_budget:256,stream:true,stream_options:{include_usage:true}});else body.reasoning_effort='low';
 const controller=new AbortController();const timeout=setTimeout(()=>controller.abort(),45000);let response;
 try{response=await upstreamFetch(config.endpoint,{method:'POST',headers:{'content-type':'application/json',authorization:'Bearer '+env[provider.key]},body:JSON.stringify(body),signal:controller.signal,redirect:'error'});
 if(!response.ok){try{await response.body?.cancel()}catch{}return {id:provider.id,model:provider.model,status:'failed',http_status:response.status,text:response.status===429?'사용량 또는 호출 한도에 도달했습니다. 자동 재시도하지 않습니다.':response.status===401||response.status===403?'키 권한·지역·모델 접근 권한을 확인해 주세요.':'공급자 요청에 실패했습니다. 자동 재시도하지 않습니다.'};}
 const reader=response.body?.getReader();let text='',bytes=0;const decoder=new TextDecoder();if(!reader)throw new Error('empty_response');while(true){const chunk=await reader.read();if(chunk.done)break;bytes+=chunk.value.byteLength;if(bytes>262144){await reader.cancel();throw new Error('response_too_large')}text+=decoder.decode(chunk.value,{stream:true})}text+=decoder.decode();const parsed=provider.id==='qwen'&&response.headers.get('content-type')?.includes('text/event-stream')?parseStream(text):parseCompletion(JSON.parse(text));return {id:provider.id,model:provider.model,...parsed};
 }catch{return {id:provider.id,model:provider.model,status:controller.signal.aborted?'timeout':'failed',text:controller.signal.aborted?'45초 제한으로 응답 대기를 중단했습니다. 서버 측 과금 중단은 보장되지 않습니다.':'응답 처리에 실패했습니다. 자동 재시도하지 않습니다.'};}finally{clearTimeout(timeout)}
}
export function createWorker({assets={},upstreamFetch=(...args)=>fetch(...args)}={}){return {async fetch(request,env={}){
 const url=new URL(request.url);const owner=isOwner(request,env);const ready=PROVIDERS.map(p=>({id:p.id,name:p.name,model:p.model,...configuration(p,env)}));
 if(url.pathname==='/api/integrations'&&request.method==='GET')return json({owner,owner_configured:!!env.MODEL_OWNER_EMAIL,providers:ready.map(({endpoint,...p})=>p),limits:{prompt_chars:2000,max_output_tokens:1024,timeout_seconds:45,automatic_retries:0},external_verifiers:{sympy:false,lean:false}});
 if(url.pathname==='/api/solve'&&request.method==='POST'){
  if(!owner)return json({error:'owner_required',message:'모델 실행은 사이트 소유자 로그인 후 사용할 수 있습니다.'},403);
  if(request.headers.get('origin')!==url.origin)return json({error:'origin_rejected'},403);
  if(!request.headers.get('content-type')?.startsWith('application/json'))return json({error:'json_required'},415);
  if(Number(request.headers.get('content-length')??0)>12000)return json({error:'body_too_large'},413);
  let body;try{const raw=await request.text();if(new TextEncoder().encode(raw).length>12000)return json({error:'body_too_large'},413);body=JSON.parse(raw)}catch{return json({error:'invalid_json'},400)}
  if(typeof body.prompt!=='string'||!body.prompt.trim()||body.prompt.length>2000)return json({error:'invalid_prompt'},400);
  if(body.confirm_cost!==true)return json({error:'cost_confirmation_required',message:'사용량 과금 가능성을 확인한 뒤 실행해 주세요.'},400);
  if(!Array.isArray(body.providers)||body.providers.length<1||body.providers.length>3||new Set(body.providers).size!==body.providers.length||body.providers.some(id=>!PROVIDERS.some(p=>p.id===id)))return json({error:'invalid_providers'},400);
  const requested=PROVIDERS.filter(p=>body.providers.includes(p.id));if(!requested.some(p=>configuration(p,env).ready))return json({error:'no_configured_provider',message:'등록된 API 키가 없습니다. 외부 호출은 실행되지 않았습니다.'},503);
  const results=await Promise.all(requested.map(p=>runProvider(p,body.prompt.trim(),env,upstreamFetch)));
  return json({results,calculation_status:'not_externally_verified',verification:'모델 풀이입니다. SymPy·Lean 검증을 실행하지 않았습니다.',created_at:new Date().toISOString()});
 }
 if(url.pathname.startsWith('/api/'))return json({error:'not_found'},404);
 if(!['GET','HEAD'].includes(request.method))return new Response('Method not allowed',{status:405});
 const path=url.pathname==='/'?'/index.html':url.pathname;const file=assets[path];if(!file)return new Response('Not found',{status:404});
 return new Response(request.method==='HEAD'?null:file.body,{headers:{'content-type':file.type,'cache-control':'no-cache','x-content-type-options':'nosniff','referrer-policy':'same-origin','content-security-policy':"default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; base-uri 'none'; form-action 'self'"}});
}};}
export default createWorker({assets:typeof __SITE_ASSETS__==='undefined'?{}:__SITE_ASSETS__});
