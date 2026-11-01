from pathlib import Path
import json,shutil,os,subprocess
root=Path(__file__).resolve().parents[2]/'ZCode'
stage=Path(__file__).resolve().parents[1]/'build/studio'
stage.mkdir(parents=True,exist_ok=True)
packages={}
for f in [*root.glob('packages/*/package.json'),*root.glob('apps/zcode-cli/packages/*/package.json')]:
 p=json.loads(f.read_text());packages[p['name']]=(p,f.parent)
seen=set(); deps={}
def visit(name):
 if name in seen:return
 seen.add(name); p,src=packages[name]
 dst=stage/src.relative_to(root)
 shutil.copytree(src,dst,dirs_exist_ok=True,ignore=shutil.ignore_patterns('node_modules','.turbo','dist'))
 for n,v in p.get('dependencies',{}).items():
  if n in packages:visit(n)
  elif not n.startswith('@lydell/node-pty-'):deps[n]=v
for n in ['@zcode/web','@zcode/server']:visit(n)
for n in ['@tailwindcss/vite','@vitejs/plugin-react','tailwindcss','vite']:
 deps[n]=packages['@zcode/web'][0]['devDependencies'][n]
deps.update({'d3-path':'3.1.0','esbuild':'0.25.12','@tailwindcss/typography':'^0.5.19'})
for d in ['scripts','config','public']:
 shutil.copytree(root/d,stage/d,dirs_exist_ok=True)
for f in ['package.json','THIRD-PARTY-NOTICES.md','NOTICE.md','tsconfig.base.json','LICENSE','.env.production']:
 shutil.copy(root/f,stage/f)
p=json.loads((stage/'package.json').read_text()); (stage/'source-package.json').write_text(json.dumps(p))
(stage/'package.json').write_text(json.dumps({'private':True,'version':p['version'],'type':'module','dependencies':deps}))
(stage/'links.json').write_text(json.dumps({n:str(packages[n][1].relative_to(root)) for n in seen}))

subprocess.run(["patch","--batch","--forward","-p1","-i", str(Path(__file__).with_name("composer-ime.patch").resolve())],cwd=stage,check=True)
