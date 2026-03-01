const API_BASE_URL = "http://localhost:8080/api/v1";

export const fileService = {
  async getPresignedUrl(file, ownerId) {
    const response = await fetch(`${API_BASE_URL}/files/presign`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        ownerId: ownerId,
        originalName: file.name,
        contentType: file.type || "application/octet-stream",
        sizeBytes: file.size,
        status: "Pending",
      }),
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
};
