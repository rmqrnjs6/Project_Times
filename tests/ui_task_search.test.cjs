const test=require('node:test');
const assert=require('node:assert/strict');
const {harness}=require('./helpers/workbench-harness.cjs');
const row=question=>({id:question,question,status:'QUEUED',version:0});
const page=(items,index=0,total=items.length,hasNext=false)=>({items,page:index,size:20,total,hasNext});
test('search trims input, encodes literal characters, and starts at page zero',async()=>{
  const h=harness(new Map(),undefined,async params=>page([row(params.get('q'))]));
  await h.refresh();
  assert.equal(typeof h.element('task-search-form').onsubmit,'function');
  await h.search('  100%_value  ');
  assert.equal(h.pageRequests.at(-1).get('q'),'100%_value');
  assert.equal(h.pageRequests.at(-1).get('page'),'0');
  assert.match(h.element('task-list').children[0].children[0].textContent,/100%_value/);
});
test('next, previous and clear preserve query and page controls',async()=>{
  const h=harness(new Map(),undefined,async params=>page([row('item')],Number(params.get('page')),21,params.get('page')==='0'));
  await h.refresh();await h.search('topic');assert.equal(h.element('task-next').disabled,false);
  await h.next();assert.equal(h.pageRequests.at(-1).get('page'),'1');assert.equal(h.pageRequests.at(-1).get('q'),'topic');
  assert.equal(h.element('task-prev').disabled,false);assert.equal(h.element('task-next').disabled,true);
  await h.previous();assert.equal(h.pageRequests.at(-1).get('page'),'0');
  await h.clear();assert.equal(h.pageRequests.at(-1).get('q'),'');assert.equal(h.element('task-prev').disabled,true);
});
test('late old search response cannot replace newer results',async()=>{
  const pending=new Map();
  const h=harness(new Map(),undefined,params=>params.get('q')?new Promise(resolve=>pending.set(params.get('q'),resolve)):page([]));
  await h.refresh();
  const older=h.search('old');const newer=h.search('new');
  pending.get('new')(page([row('new result')]));await newer;
  pending.get('old')(page([row('old result')]));await older;
  assert.match(h.element('task-list').children[0].children[0].textContent,/new result/);
});
test('failed search keeps prior results and stale failure cannot overwrite a newer success',async()=>{
  let rejectOld;
  const h=harness(new Map(),undefined,params=>{
    if(params.get('q')==='broken')throw Error('synthetic offline');
    if(params.get('q')==='slow')return new Promise((_,reject)=>{rejectOld=reject;});
    return page([row('kept result')]);
  });
  await h.refresh();await h.search('broken');
  assert.match(h.element('task-message').textContent,/synthetic offline/);
  assert.match(h.element('task-list').children[0].children[0].textContent,/kept result/);
  const older=h.search('slow');await h.search('new');rejectOld(Error('old offline'));await older;
  assert.equal(h.element('task-message').textContent,'');
});

test('empty search disables paging without clearing selected manual note draft',async()=>{
  const h=harness();await h.refresh();await h.select();h.fill();
  await h.search('none');
  assert.equal(h.element('note-original').value,'synthetic original only');
  assert.equal(h.element('note-interpretation').value,'synthetic interpretation only');
  assert.equal(h.element('task-prev').disabled,true);assert.equal(h.element('task-next').disabled,true);
  assert.equal(h.element('task-list').children.length,1);
});
