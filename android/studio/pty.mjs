import {spawn as launch} from 'node:child_process';
import path from 'node:path';
export function spawn(shell,args,options){
 const child=launch(path.join(process.env.ZCODE_ANDROID_RUNTIME,'bin/pty'),[shell,String(options.rows),String(options.cols),'control'],{cwd:options.cwd,env:options.env,stdio:['pipe','pipe','inherit','pipe']});
 child.stdout.setEncoding('utf8');
 for(const stream of [child.stdin,child.stdio[3]])stream.on('error',()=>{});
 return {
  onData(cb){child.stdout.on('data',cb);return {dispose:()=>child.stdout.off('data',cb)};},
  onExit(cb){const listener=code=>cb({exitCode:code??1});child.on('close',listener);return {dispose:()=>child.off('close',listener)};},
  write(text){child.stdin.write(text);},
  resize(cols,rows){const bytes=Buffer.alloc(4);bytes.writeUInt16LE(rows,0);bytes.writeUInt16LE(cols,2);child.stdio[3].write(bytes);},
  kill(){child.stdin.end();}
 };
}
