import fs from 'node:fs';
import path from 'node:path';
const src = path.resolve('../ZCode');
const packages = new Map();
for (const base of ['packages','apps/zcode-cli/packages']) {
  for (const name of fs.readdirSync(path.join(src,base))) {
    const dir=path.join(src,base,name);
    if(fs.existsSync(path.join(dir,'package.json'))) {
      const pkg=JSON.parse(fs.readFileSync(path.join(dir,'package.json')));
      packages.set(pkg.name,{dir,pkg});
    }
  }
}
const deps={esbuild:'0.25.12'}, aliases={}, seen=new Set();
function visit(name) {
  if(seen.has(name)) return;
  seen.add(name);
  const {dir,pkg}=packages.get(name);
  for(const [key,entry] of Object.entries(pkg.exports||{'.':pkg.main||'./src/index.ts'})){
    const target=typeof entry==='string'?entry:entry.import||entry.default;
    if(!target) continue;
    const file=path.resolve(dir,target.replace(/^\.\/dist\//,'./src/').replace(/\.js$/,'.ts'));
    aliases[name+key.slice(1)]=fs.existsSync(file)?file:path.resolve(dir,target);
  }
  for(const [id,version] of Object.entries(pkg.dependencies||{})){
    if(['@zcode/tui','playwright-core','koffi'].includes(id)) continue;
    if(packages.has(id)) visit(id); else deps[id]=version;
  }
}
visit('@zcode/cli');
fs.mkdirSync('build',{recursive:true});
fs.writeFileSync('build/package.json',JSON.stringify({private:true,dependencies:deps},null,2));
if(process.argv.includes('--bundle')){
 const {build}=await import('./build/node_modules/esbuild/lib/main.js');
 await build({entryPoints:[src+'/apps/zcode-cli/packages/cli/src/main.ts'],bundle:true,platform:'node',format:'cjs',target:'node24',alias:aliases,nodePaths:[path.resolve('build/node_modules')],external:['@zcode/tui','playwright-core','koffi'],define:{__CLI_VERSION__:JSON.stringify(JSON.parse(fs.readFileSync(src+'/apps/zcode-cli/package.json')).version)},outfile:'build/assets/zcode.cjs'});
}
