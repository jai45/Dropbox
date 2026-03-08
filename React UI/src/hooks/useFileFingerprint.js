import { useState, useCallback, useRef } from "react";
import { createSHA256 } from "hash-wasm";

/**
 * Files larger than this threshold are hashed inside a dedicated Web Worker
 * so the main thread (and therefore the UI) stays fully responsive.
 * Files at or below this limit are hashed directly on the main thread using
 * hash-wasm's async streaming API, which is fast enough not to cause jank.
 */
const WORKER_THRESHOLD = 10 * 1024 * 1024; // 10 MB

/** Read-chunk size used when streaming the file through the hasher. */
const CHUNK_SIZE = 2 * 1024 * 1024; // 2 MB

// ─── helpers ────────────────────────────────────────────────────────────────

/**
 * Returns a stable string key that uniquely identifies a File instance
 * across renders without relying on object identity.
 */
export const fileKey = (file) =>
  `${file.name}:${file.size}:${file.lastModified}`;

/**
 * Hash a small file on the main thread.
 * Each `arrayBuffer()` call is awaited, which keeps the event-loop ticking
 * between chunks and prevents noticeable frame drops for reasonable file sizes.
 */
async function hashOnMainThread(file, onProgress) {
  const hasher = await createSHA256();
  hasher.init();

  let offset = 0;

  while (offset < file.size) {
    const buffer = await file.slice(offset, offset + CHUNK_SIZE).arrayBuffer();
    hasher.update(new Uint8Array(buffer));
    offset += CHUNK_SIZE;

    onProgress(Math.round(Math.min(100, (offset / file.size) * 100)));
  }

  return hasher.digest("hex");
}

/**
 * Hash a large file inside a dedicated Web Worker.
 * Vite resolves the worker URL at build-time and bundles it as a separate
 * chunk, so the module import inside the worker works without extra config.
 */
function hashInWorker(file, onProgress) {
  return new Promise((resolve, reject) => {
    const worker = new Worker(
      new URL("../workers/hashWorker.js", import.meta.url),
      { type: "module" },
    );

    // Use a unique id so multiple concurrent workers don't cross-talk.
    const id = crypto.randomUUID();

    worker.onmessage = ({ data }) => {
      if (data.id !== id) return;

      if (data.type === "progress") {
        onProgress(data.progress);
      } else if (data.type === "done") {
        worker.terminate();
        resolve(data.hash);
      } else if (data.type === "error") {
        worker.terminate();
        reject(new Error(data.error));
      }
    };

    worker.onerror = (e) => {
      worker.terminate();
      reject(new Error(e.message ?? "Worker error"));
    };

    // File is structured-cloneable; only metadata is copied – file data is
    // read lazily inside the worker via slice().arrayBuffer().
    worker.postMessage({ id, file });
  });
}

// ─── hook ───────────────────────────────────────────────────────────────────

/**
 * @typedef {{ hash: string | null, status: 'pending' | 'hashing' | 'done' | 'error', progress: number, error?: string }} FingerprintEntry
 */

/**
 * useFileFingerprint
 *
 * Computes a SHA-256 content fingerprint for every file in a list.
 *
 * • Files ≤ 10 MB  →  hashed on the main thread (async, chunked)
 * • Files  > 10 MB →  hashed in a Web Worker (fully off-thread)
 *
 * Usage
 * -----
 * ```jsx
 * const { fingerprintMap, computeFingerprints, isHashing, resetFingerprints } =
 *   useFileFingerprint();
 *
 * // kick off hashing whenever the file list changes
 * useEffect(() => {
 *   computeFingerprints(files);
 *   return () => resetFingerprints();
 * }, [files]);
 *
 * // read a specific file's status
 * const entry = fingerprintMap[fileKey(file)];
 * // entry.status  → 'pending' | 'hashing' | 'done' | 'error'
 * // entry.hash    → 'a3f9…'  (hex SHA-256, available when status === 'done')
 * // entry.progress → 0-100
 * ```
 *
 * @returns {{
 *   fingerprintMap: Record<string, FingerprintEntry>,
 *   computeFingerprints: (files: File[]) => Promise<void>,
 *   isHashing: boolean,
 *   resetFingerprints: () => void,
 * }}
 */
export function useFileFingerprint() {
  /** @type {[Record<string, FingerprintEntry>, Function]} */
  const [fingerprintMap, setFingerprintMap] = useState({});
  const [isHashing, setIsHashing] = useState(false);

  // A ref-based cancel flag avoids stale-closure issues with async tasks.
  const cancelledRef = useRef(false);

  const updateEntry = useCallback((key, patch) => {
    setFingerprintMap((prev) => ({
      ...prev,
      [key]: { ...prev[key], ...patch },
    }));
  }, []);

  const computeFingerprints = useCallback(
    async (files) => {
      if (!files.length) return;

      cancelledRef.current = false;

      // Seed all entries as pending before any async work begins.
      setFingerprintMap(
        Object.fromEntries(
          files.map((f) => [
            fileKey(f),
            { hash: null, status: "pending", progress: 0 },
          ]),
        ),
      );
      setIsHashing(true);

      await Promise.all(
        files.map(async (file) => {
          const key = fileKey(file);
          if (cancelledRef.current) return;

          updateEntry(key, { status: "hashing" });

          try {
            const onProgress = (progress) => {
              if (!cancelledRef.current) updateEntry(key, { progress });
            };

            const hash =
              file.size > WORKER_THRESHOLD
                ? await hashInWorker(file, onProgress)
                : await hashOnMainThread(file, onProgress);

            if (!cancelledRef.current) {
              updateEntry(key, { hash, status: "done", progress: 100 });
            }
          } catch (err) {
            if (!cancelledRef.current) {
              updateEntry(key, {
                status: "error",
                error: err.message,
                progress: 0,
              });
            }
          }
        }),
      );

      if (!cancelledRef.current) setIsHashing(false);
    },
    [updateEntry],
  );

  /** Cancel any in-flight hashing and clear the map. */
  const resetFingerprints = useCallback(() => {
    cancelledRef.current = true;
    setFingerprintMap({});
    setIsHashing(false);
  }, []);

  return { fingerprintMap, computeFingerprints, isHashing, resetFingerprints };
}
