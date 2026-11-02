// Keep the pinned upstream checkout pristine. Android sockets can also report
// ECONNABORTED when a read is interrupted during a network transition.
import {readFile} from 'node:fs/promises';
import path from 'node:path';

export const androidNetworkErrors = {
  name: 'android-network-errors',
  setup(build) {
    build.onLoad({filter: /[/\\]adapters[/\\]src[/\\]model[/\\]failure-inspection\.ts$/}, async ({path: file}) => {
      const source = await readFile(file, 'utf8');
      const anchor = 'normalized === "ECONNRESET" ||';
      if (source.split(anchor).length !== 2) throw Error('Review Android transport patch against upstream failure classifier');
      return {contents: source.replace(anchor, anchor+'\n    normalized === "ECONNABORTED" ||'), loader: 'ts', resolveDir: path.dirname(file)};
    });
  }
};
