import { apiClient } from "../utils/apiClient";

const MULTIPART_THRESHOLD = 10 * 1024 * 1024; // 10 MB
const CHUNK_SIZE = 5 * 1024 * 1024; // 5 MB
const CONCURRENCY_LIMIT = 6; // Max parallel uploads at a time

// Runs `tasks` with at most `limit` running at once
const concurrentLimit = async (tasks, limit) => {
  const results = new Array(tasks.length);
  let index = 0;

  const worker = async () => {
    while (index < tasks.length) {
      const current = index++;
      results[current] = await tasks[current]();
    }
  };

  // Spin up `limit` workers — each worker picks the next task when free
  await Promise.all(
    Array.from({ length: Math.min(limit, tasks.length) }, worker),
  );

  return results;
};

export const fileService = {
  async getPresignedUrl(file, ownerId, sha256, signal) {
    const response = await apiClient.post(
      "/files/presign",
      {
        ownerId: ownerId,
        originalName: file.name,
        contentType: file.type || "application/octet-stream",
        sizeBytes: file.size,
        status: "Pending",
        contentHash: sha256,
      },
      { signal },
    );

    if (!response.ok) {
      throw new Error("Failed to get presigned URL");
    }

    return response.json();
  },

  async uploadToPresignedUrl(uploadUrl, file, signal) {
    const response = await fetch(uploadUrl, {
      method: "PUT",
      headers: {
        "Content-Type": file.type || "application/octet-stream",
      },
      body: file,
      signal,
    });

    if (!response.ok) {
      throw new Error("Failed to upload file");
    }

    return response;
  },

  async downloadFile(fileId) {
    // Call API with auth to get presigned download URL
    const response = await apiClient.get(`/files/${fileId}/download`);

    if (!response.ok) {
      throw new Error("Failed to get download URL");
    }

    const data = await response.json();
    return data.downloadUrl; // Return the presigned URL
  },

  async getAllFiles() {
    const response = await apiClient.get("/files");

    if (!response.ok) {
      throw new Error("Failed to fetch files");
    }

    return response.json();
  },

  async deleteFile(fileId) {
    const response = await apiClient.delete(`/files/${fileId}`);

    if (!response.ok) {
      throw new Error("Failed to delete file");
    }

    return true;
  },

  async confirmUpload(fileId, contentHash) {
    const response = await apiClient.post(`/files/${fileId}/confirm`, {
      contentHash,
    });

    return response.ok;
  },

  // ── Multipart upload ──────────────────────────────────────────────────────

  isMultipart(file) {
    return file.size > MULTIPART_THRESHOLD;
  },

  async initiateMultipartUpload(file, ownerId, sha256, signal) {
    const partCount = Math.ceil(file.size / CHUNK_SIZE);

    const response = await apiClient.post(
      "/multipart/initiate",
      {
        ownerId,
        originalName: file.name,
        contentType: file.type || "application/octet-stream",
        sizeBytes: file.size,
        partCount,
        contentHash: sha256,
      },
      { signal },
    );

    if (!response.ok) {
      throw new Error("Failed to initiate multipart upload");
    }

    // Returns: { uploadId, fileId, objectKey, parts: [{ partNumber, uploadUrl }] }
    return response.json();
  },

  async uploadPart(uploadUrl, chunk, partNumber, signal) {
    const response = await fetch(uploadUrl, {
      method: "PUT",
      headers: {
        "Content-Type": "application/octet-stream",
      },
      body: chunk,
      signal,
    });

    if (!response.ok) {
      throw new Error(`Failed to upload part ${partNumber}`);
    }

    // R2/S3 returns ETag in the response header
    const etag = response.headers.get("ETag") || response.headers.get("etag");
    return { partNumber, etag };
  },

  async completeMultipartUpload(uploadSessionId, parts) {
    const response = await apiClient.post("/multipart/complete", {
      uploadSessionId,
      parts: parts.map(({ partNumber, etag }) => ({
        partNumber,
        eTag: etag, // backend expects eTag (capital T)
      })),
    });

    if (!response.ok) {
      throw new Error("Failed to complete multipart upload");
    }

    return response.json();
  },

  async abortMultipartUpload(uploadSessionId) {
    try {
      await apiClient.post("/multipart/abort", { uploadSessionId });
    } catch (error) {
      console.error("Failed to abort multipart upload:", error);
    }
  },

  async multipartUpload(file, ownerId, onProgress, signal, sha256) {
    let uploadSessionId = null;

    try {
      // Step 1: Initiate
      onProgress(5);
      const initResponse = await this.initiateMultipartUpload(
        file,
        ownerId,
        sha256,
        signal,
      );
      uploadSessionId = initResponse.uploadSessionId;
      const parts = initResponse.parts;

      onProgress(10);

      // If the server already has this content (deduplication), skip uploading
      if (initResponse.deduplicated) {
        // Simulate the same progress milestones so the UI feels identical
        onProgress(92);
        onProgress(100);
        return {
          fileId: initResponse.fileId,
          objectKey: initResponse.objectKey,
        };
      }

      // Step 2: Upload all chunks with a concurrency limit of 6
      let completedParts = 0;
      const tasks = parts.map(({ partNumber, presignedUrl }) => async () => {
        if (signal?.aborted) throw new DOMException("Aborted", "AbortError");
        const start = (partNumber - 1) * CHUNK_SIZE;
        const end = Math.min(start + CHUNK_SIZE, file.size);
        const chunk = file.slice(start, end);

        const result = await this.uploadPart(
          presignedUrl,
          chunk,
          partNumber,
          signal,
        );

        completedParts++;
        onProgress(
          Math.round(Math.min(90, 10 + (completedParts / parts.length) * 80)),
        );

        return result;
      });

      const partResults = await concurrentLimit(tasks, CONCURRENCY_LIMIT);

      // Step 3: Complete
      onProgress(92);
      await this.completeMultipartUpload(uploadSessionId, partResults);
      onProgress(100);

      return { fileId: initResponse.fileId, objectKey: initResponse.objectKey };
    } catch (error) {
      // Abort to clean up dangling parts on R2
      if (uploadSessionId) {
        await this.abortMultipartUpload(uploadSessionId);
      }
      throw error;
    }
  },
};
