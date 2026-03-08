/**
 * hashWorker.js
 *
 * Web Worker that streams a File through SHA-256 in 2 MB chunks using hash-wasm.
 * Runs off the main thread so large-file hashing never blocks the UI.
 *
 * Protocol
 * --------
 * Incoming:  { id: string, file: File }
 * Outgoing:  { type: 'progress', id, progress: number (0-100) }
 *          | { type: 'done',     id, hash: string (hex) }
 *          | { type: 'error',    id, error: string }
 */

import { createSHA256 } from "hash-wasm";

const CHUNK_SIZE = 2 * 1024 * 1024; // 2 MB read-chunks

self.onmessage = async ({ data: { id, file } }) => {
  try {
    const hasher = await createSHA256();
    hasher.init();

    let offset = 0;

    while (offset < file.size) {
      const buffer = await file
        .slice(offset, offset + CHUNK_SIZE)
        .arrayBuffer();
      hasher.update(new Uint8Array(buffer));
      offset += CHUNK_SIZE;

      const progress = Math.round(Math.min(100, (offset / file.size) * 100));
      self.postMessage({ type: "progress", id, progress });
    }

    const hash = hasher.digest("hex");
    self.postMessage({ type: "done", id, hash });
  } catch (err) {
    self.postMessage({ type: "error", id, error: err.message });
  }
};
