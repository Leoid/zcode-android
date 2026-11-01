process.stdin.resume();
process.stdin.on('end',()=>process.exit(0));
await import('./server.mjs');
