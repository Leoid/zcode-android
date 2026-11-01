"""Collect Termux's ARM64 Node, Bash and their shared libraries for the APK."""
import concurrent.futures, gzip, hashlib, io, pathlib, subprocess, tempfile, urllib.request, zipfile
root = 'https://packages.termux.dev/apt/termux-main/'
index = gzip.decompress(urllib.request.urlopen(root+'dists/stable/main/binary-aarch64/Packages.gz').read()).decode()
packages = {}
for block in index.split('\n\n'):
    fields = dict(line.split(': ',1) for line in block.splitlines() if ': ' in line and not line.startswith(' '))
    if 'Package' in fields: packages[fields['Package']] = fields
def fetch(name):
    p = packages[name]
    data = urllib.request.urlopen(root+p['Filename']).read()
    assert hashlib.sha256(data).hexdigest() == p['SHA256']
    with tempfile.TemporaryDirectory() as tmp:
        deb = pathlib.Path(tmp,'pkg.deb')
        deb.write_bytes(data)
        subprocess.run(['tar','xf',str(deb)],cwd=tmp,check=True)
        tar = next(pathlib.Path(tmp).glob('data.tar.*'))
        subprocess.run(['tar','xf',str(tar)],cwd=tmp,check=True)
        prefix = pathlib.Path(tmp,'data/data/com.termux/files/usr')
        files = {}
        for f in [*prefix.glob('lib/*.so*'),prefix/'bin/node',prefix/'bin/bash',prefix/'bin/git',prefix/'bin/rg',prefix/'bin/less',prefix/'libexec/git-core/git-remote-https',prefix/'libexec/git-core/git-remote-http',prefix/'etc/tls/cert.pem',*prefix.glob('share/git-core/templates/**/*')]:
            if f.is_file(): files[str(f.relative_to(prefix))] = f.read_bytes()
        return files
out = pathlib.Path('build/assets')
out.mkdir(parents=True,exist_ok=True)
names = ['nodejs-lts','bash','libc++','openssl','c-ares','libicu','libsqlite','zlib','libandroid-support','libiconv','readline','ncurses','libandroid-glob','libandroid-posix-semaphore','git','ripgrep','less','libcurl','libexpat','pcre2','libnghttp2','libnghttp3','libngtcp2','libssh2','ca-certificates']
with zipfile.ZipFile(out/'runtime.zip','w',zipfile.ZIP_DEFLATED,compresslevel=1) as z:
    for files in concurrent.futures.ThreadPoolExecutor(4).map(fetch,names):
        for name,data in files.items(): z.writestr(name,data)
