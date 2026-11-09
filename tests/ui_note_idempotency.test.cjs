const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const {webcrypto, randomUUID} = require('node:crypto');
const source = fs.readFileSync('web/src/main/resources/static/workbench.js', 'utf8');
const task = '812cba03-a324-4df3-9808-e7fc1e257332';
const example = {sourceUrl:'https://example.org/source', originalText:'synthetic original only', interpretation:'synthetic interpretation only', stance:'UNVERIFIED'};
function harness(storage = new Map(), post = async () => {}) {
  const elements = new Map();
  const element = id => {
    if (!elements.has(id)) elements.set(id, {value:'', textContent:'', disabled:false, replaceChildren(){}, append(){}});
    return elements.get(id);
  };
  const calls=[];
  element('note-form').reset = () => fill({sourceUrl:'',originalText:'',interpretation:'',stance:'UNVERIFIED'});
  function fill(payload=example) {
    for (const [key,id] of Object.entries({sourceUrl:'note-source',originalText:'note-original',interpretation:'note-interpretation',stance:'note-stance'})) element(id).value=payload[key];
  }
  const context = vm.createContext({TextEncoder, Uint8Array, crypto:{subtle:webcrypto.subtle,randomUUID},
    sessionStorage:{getItem:key=>storage.get(key)||null,setItem:(key,value)=>storage.set(key,value),removeItem:key=>storage.delete(key)},
    document:{getElementById:element,querySelector:()=>({content:'synthetic-csrf'}),createElement:()=>({append(){}})},
    window:{location:{assign(){}}},
    fetch:async (path, options={}) => {
      if (path.endsWith('/notes')) {
        calls.push({key:options.headers['Idempotency-Key'],payload:JSON.parse(options.body)});
        await post(calls.length);
        return {status:201,ok:true,json:async()=>({id:'synthetic-note'})};
      }
      const data = path==='/api/tasks' || path==='/api/checks' ? [] : {id:task,question:'synthetic',status:'QUEUED',version:0,notes:[]};
      return {status:200,ok:true,json:async()=>data};
    }
  });
  vm.runInContext(source,context);
  return {storage,calls,element,fill,
    select:()=>vm.runInContext(`showTask('${task}')`,context),
    submit:()=>element('note-form').onsubmit({preventDefault(){}})};
}
test('lost response retries the same key; accepted intentional repetition gets a new key', async () => {
  const h=harness(new Map(),async count=>{if(count===1) throw Error('synthetic lost reply');});
  await h.select();h.fill();await h.submit();
  assert.equal(h.storage.size,1);
  assert.ok(![...h.storage.values()][0].includes(example.originalText));
  await h.submit();
  assert.equal(h.calls[0].key,h.calls[1].key);assert.equal(h.storage.size,0);
  h.fill();await h.submit();assert.notEqual(h.calls[1].key,h.calls[2].key);
});
test('tab reload with unchanged input reuses the pending key', async () => {
  const cache=new Map();const a=harness(cache,async()=>{throw Error('synthetic lost reply');});
  await a.select();a.fill();await a.submit();
  const b=harness(cache);await b.select();b.fill();await b.submit();
  assert.equal(a.calls[0].key,b.calls[0].key);
});
test('edited input after an ambiguous response is a new submission', async () => {
  const h=harness(new Map(),async count=>{if(count===1) throw Error('synthetic lost reply');});
  await h.select();h.fill();await h.submit();
  h.fill({...example,interpretation:'correction reason'});await h.submit();
  assert.notEqual(h.calls[0].key,h.calls[1].key);
});
test('two simultaneous submit events send one request', {timeout:5000}, async () => {
  let release, entered;const waiting=new Promise(resolve=>{release=resolve;});
  const ready=new Promise(resolve=>{entered=resolve;});
  const h=harness(new Map(),()=>{entered();return waiting;});await h.select();h.fill();
  const first=h.submit();
  await ready;
  assert.equal(h.calls.length,1);
  await h.submit();assert.equal(h.calls.length,1);release();await first;
});
test('blocked session storage prevents an untracked save', async () => {
  const broken={get(){throw Error('synthetic storage blocked');}};
  const h=harness(broken);await h.select();h.fill();await h.submit();
  assert.equal(h.calls.length,0);assert.equal(h.element('note-fields').disabled,false);
});
