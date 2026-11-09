const test = require('node:test');
const assert = require('node:assert/strict');
const {harness,task,example}=require('./helpers/workbench-harness.cjs');
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
