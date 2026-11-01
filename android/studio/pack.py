from pathlib import Path
import zipfile,shutil
stage=Path('build/studio-runtime')
pty=stage/'node_modules/node-pty'
pty.mkdir(parents=True,exist_ok=True)
shutil.copy('studio/pty.mjs',pty/'index.mjs')
(pty/'package.json').write_text('{"type":"module","exports":"./index.mjs"}')
shutil.copy('studio/host.mjs',stage)
web=stage/'web'
for name in ('mobile.css','mobile.js'):shutil.copy(Path('studio')/name,web/name)
index=web/'index.html'
html=index.read_text()
if '/mobile.css' not in html:index.write_text(html.replace('</head>','<link rel="stylesheet" href="/mobile.css"><script defer src="/mobile.js"></script></head>'))
with zipfile.ZipFile('build/assets/studio.zip','w',zipfile.ZIP_DEFLATED,compresslevel=3) as z:
 for f in stage.rglob('*'):
  if f.is_file() and f.suffix not in ('.map','.ts','.node'):z.write(f,f.relative_to(stage))
