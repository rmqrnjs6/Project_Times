const test = require('node:test');
const assert = require('node:assert/strict');
const {scoreQ6} = require('../public/scoring.cjs');
test('documented synthetic candidates reproduce 80 and 75 with exact integer numerators',()=>{
 const c=scoreQ6([85,85,74.375,74.375,74.375,25.625]);
 const a=scoreQ6([74.8,76.5,74.375,74.375,74.375,25.625]);
 assert.equal(c.total,80);assert.equal(a.total,75);assert.equal(c.total-a.total,5);
 assert.equal(BigInt(c.numerator)-BigInt(a.numerator),425000n);
});
test('risk is inverted once and all normalized boundaries remain bounded',()=>{
 assert.equal(scoreQ6([100,100,100,100,100,0]).total,100);
 assert.equal(scoreQ6([0,0,0,0,0,100]).total,0);
 assert.equal(scoreQ6([0,0,0,0,0,0]).total,1000/85);
});
test('missing and invalid raw scores cannot silently become a score',()=>{
 for(const value of [null,[],[80,80,80], [80,80,80,80,80,null],[101,0,0,0,0,0],[-1,0,0,0,0,0],[NaN,0,0,0,0,0],[Infinity,0,0,0,0,0],[.0001,0,0,0,0,0]])assert.throws(()=>scoreQ6(value),TypeError);
});
