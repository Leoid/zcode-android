import fs from 'node:fs';
import path from 'node:path';
import {createRequire} from 'node:module';
import {execFileSync} from 'node:child_process';
const stage=path.resolve('build/studio');
const require=createRequire(stage+'/package.json');
for(const [name,rel] of Object.entries(require('./links.json'))){
 const dst=stage+'/node_modules/'+name;
 fs.mkdirSync(path.dirname(dst),{recursive:true});
 if(!fs.existsSync(dst))fs.symlinkSync(stage+'/'+rel,dst,'dir');
}
const out=path.resolve('build/studio-runtime');
fs.mkdirSync(out,{recursive:true});
fs.copyFileSync("studio/android-host.ts",stage+"/android-host.ts");
const bundler=require('esbuild');
const external=['ssh2','undici','ws','node-pty','node-forge','yazl','yauzl','axios','yaml','form-data','combined-stream','proxy-from-env','follow-redirects'];
await bundler.build({entryPoints:[stage+'/android-host.ts'],format:'esm',platform:'node',target:'node24',bundle:true,outfile:out+'/server.mjs',external,define:{__ZCODE_VERSION__:JSON.stringify(require('./package.json').version),__ZCODE_MIN_COMPATIBLE_VERSION__:'"3.17.1"',__ZCODE_ENV__:'"production"',__ZCODE_BUILTIN_PROVIDER_CONFIG_JSON__:JSON.stringify(fs.readFileSync(stage+'/config/provider/zcode-builtin.json','utf8'))},banner:{js:'import {createRequire as makeRequire} from "node:module";const require=makeRequire(import.meta.url);'}});
const copied=new Set();
function copy(name,from){
 if(name==='node-pty')return;
 const src=createRequire(from+'/package.json').resolve.paths(name).map(d=>path.join(d,name)).find(d=>fs.existsSync(d+'/package.json'));
 if(!src)throw Error('Missing '+name);
 const rel=path.relative(stage,src);
 if(copied.has(rel))return;copied.add(rel);
 fs.cpSync(src,path.join(out,rel),{recursive:true,filter:f=>path.basename(f)!=='node_modules'});
 for(const dep of Object.keys(JSON.parse(fs.readFileSync(src+'/package.json')).dependencies||{}))copy(dep,src);
}
for(const dep of external)copy(dep,stage);
if(process.argv.includes('--host-only'))process.exit(0);
execFileSync(process.execPath,[stage+'/node_modules/vite/bin/vite.js','build','--outDir',out+'/web','--sourcemap','false'],{cwd:stage+'/packages/web',env:{...process.env,ZCODE_ENV:'production',NODE_OPTIONS:'--max-old-space-size=5800'},stdio:'inherit'});
