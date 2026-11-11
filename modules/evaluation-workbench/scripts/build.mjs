import {readFile,writeFile,mkdir,readdir,rm} from 'node:fs/promises';
import {extname,join} from 'node:path';
const types={'.html':'text/html; charset=utf-8','.css':'text/css; charset=utf-8','.js':'text/javascript; charset=utf-8','.svg':'image/svg+xml'};
const assets={};for(const name of await readdir('public')){if(!types[extname(name)])continue;assets['/'+name]={body:await readFile(join('public',name),'utf8'),type:types[extname(name)]};}
if(!assets['/index.html'])throw new Error('Missing index.html');
const manifest=JSON.parse(await readFile('.openai/hosting.json','utf8'));if(manifest.static)throw new Error('Worker manifest must not declare static.directory');
const worker=await readFile('worker/index.mjs','utf8');
await rm('dist',{recursive:true,force:true});await mkdir('dist/server',{recursive:true});await mkdir('dist/.openai',{recursive:true});
await writeFile('dist/server/index.js','const __SITE_ASSETS__='+JSON.stringify(assets)+';\n'+worker);await writeFile('dist/.openai/hosting.json',JSON.stringify(manifest,null,2));
const compiled=await import('data:text/javascript;base64,'+Buffer.from('const __SITE_ASSETS__='+JSON.stringify(assets)+';\n'+worker).toString('base64'));
if(typeof compiled.default?.fetch!=='function')throw new Error('Worker entrypoint missing');console.log('Built Worker with '+Object.keys(assets).length+' embedded assets');
