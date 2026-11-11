// Fixed-policy arithmetic. No model inference or factuality judgement occurs here.
function scoreQ6(raw){
  if(!Array.isArray(raw)||raw.length!==6||raw.some(n=>typeof n!=='number'||!Number.isFinite(n)||n<0||n>100||Math.abs(n*1000-Math.round(n*1000))>1e-7))throw new TypeError('Q6 requires six scores from 0 to 100 with at most three decimal places.');
  const weights=[25,20,10,10,10,10];
  const numerator=raw.reduce((total,n,i)=>total+BigInt(weights[i])*BigInt(i===5?100000-Math.round(n*1000):Math.round(n*1000)),0n);
  return {total:Number(numerator)/85000,numerator:numerator.toString(),denominator:85000,policy:'q6-v1',source:'provided-synthetic-raw'};
}
if(typeof module!=='undefined')module.exports={scoreQ6};
