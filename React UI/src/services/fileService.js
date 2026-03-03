import { apiClient } from "../utils/apiClient";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL;

export const fileService = {
  async getPresignedUrl(file, ownerId) {
    const response = await apiClient.post("/files/presign", {
      ownerId: ownerId,
      originalName: file.name,
      contentType: file.type || "application/octet-stream",
      sizeBytes: file.size,
      status: "Pending",
    });

    if (!response.ok) {
      throw new Error("Failed to get presigned URL");
    }

    return response.json();
  },

  async uploadToPresignedUrl(uploadUrl, file) {
    const response = await fetch(uploadUrl, {
      method: "PUT",
      headers: {
        "Content-Type": file.type || "application/octet-stream",
      },
      body: file,
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
};
