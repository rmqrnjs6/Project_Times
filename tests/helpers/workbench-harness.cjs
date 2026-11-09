const fs = require('node:fs');
const vm = require('node:vm');
const {webcrypto, randomUUID} = require('node:crypto');
const source = fs.readFileSync(process.env.WORKBENCH_SOURCE || 'web/src/main/resources/static/workbench.js', 'utf8');
const task = '812cba03-a324-4df3-9808-e7fc1e257332';
const example = {sourceUrl:'https://example.org/source', originalText:'synthetic original only', interpretation:'synthetic interpretation only', stance:'UNVERIFIED'};
function harness(storage = new Map(), post = async () => {}, page = async params => ({items:[],page:Number(params.get('page')),size:20,total:0,hasNext:false})) {
  const elements = new Map();
  const node = () => ({value:'', textContent:'', disabled:false, children:[], replaceChildren(...children){this.children=children;}, append(...children){this.children.push(...children);}});
  const element = id => {
    if (!elements.has(id)) elements.set(id, node());
    return elements.get(id);
  };
  const calls=[]; const pageRequests=[];
  element('note-form').reset = () => fill({sourceUrl:'',originalText:'',interpretation:'',stance:'UNVERIFIED'});
  function fill(payload=example) {
    for (const [key,id] of Object.entries({sourceUrl:'note-source',originalText:'note-original',interpretation:'note-interpretation',stance:'note-stance'})) element(id).value=payload[key];
  }
  const context = vm.createContext({TextEncoder, Uint8Array, URLSearchParams, crypto:{subtle:webcrypto.subtle,randomUUID},
    sessionStorage:{getItem:key=>storage.get(key)||null,setItem:(key,value)=>storage.set(key,value),removeItem:key=>storage.delete(key)},
    document:{getElementById:element,querySelector:()=>({content:'synthetic-csrf'}),createElement:node},
    window:{location:{assign(){}}},
    fetch:async (path, options={}) => {
      if(path.startsWith('/api/tasks/page?')) {
        const params=new URLSearchParams(path.split('?',2)[1]);pageRequests.push(params);
        const data=await page(params);return {status:200,ok:true,json:async()=>data};
      }
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
  return {storage,calls,pageRequests,element,fill,
    refresh:()=>vm.runInContext('tasks()',context),
    search:query=>{element('task-search').value=query;return element('task-search-form').onsubmit({preventDefault(){}});},
    next:()=>element('task-next').onclick(),previous:()=>element('task-prev').onclick(),clear:()=>element('task-search-clear').onclick(),
    select:()=>vm.runInContext(`showTask('${task}')`,context),
    submit:()=>element('note-form').onsubmit({preventDefault(){}})};
}

module.exports={harness,task,example};
