import { authService } from "../services/authService";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL;

const isTokenExpired = () => {
  const authData = localStorage.getItem("authData");
  if (!authData) return true;

  try {
    const { expiresAt } = JSON.parse(authData);
    
    // If expiresAt is missing or invalid, consider token expired
    if (!expiresAt || typeof expiresAt !== 'number') {
      return true;
    }
    
    // Add 60 second buffer - refresh if token expires in less than 60 seconds
    const now = Date.now();
    return now >= expiresAt - 60000;
  } catch (error) {
    // If parsing fails, consider token expired
    return true;
  }
};

const refreshTokenIfNeeded = async () => {
  if (!isTokenExpired()) {
    return true;
  }

  try {
    const response = await authService.refreshToken();

    const authData = {
      accessToken: response.accessToken,
      accessExpiresIn: response.accessExpiresIn,
      expiresAt: Date.now() + response.accessExpiresIn,
    };

    localStorage.setItem("authData", JSON.stringify(authData));
    return true;
  } catch (error) {
    console.error("Token refresh failed:", error);
    // Clear auth data on refresh failure
    localStorage.removeItem("user");
    localStorage.removeItem("authData");
    return false;
  }
};

const getAuthHeaders = () => {
  const authData = localStorage.getItem("authData");
  if (authData) {
    const { accessToken } = JSON.parse(authData);
    return {
      "Content-Type": "application/json",
      Authorization: `Bearer ${accessToken}`,
    };
  }
  return {
    "Content-Type": "application/json",
  };
};

export const apiClient = {
  async fetch(url, options = {}) {
    // Refresh token if needed before making the request
    const isAuthenticated = await refreshTokenIfNeeded();

    if (!isAuthenticated && options.requiresAuth !== false) {
      throw new Error("Authentication required");
    }

    const headers =
      options.requiresAuth !== false ? getAuthHeaders() : options.headers;

    const response = await fetch(`${API_BASE_URL}${url}`, {
      ...options,
      credentials: "include",
      headers: {
        ...headers,
        ...options.headers,
      },
    });

    // If we get 401, try refreshing token once and retry
    if (response.status === 401 && options.requiresAuth !== false) {
      const refreshed = await refreshTokenIfNeeded();

      if (refreshed) {
        // Retry the request with new token
        const retryHeaders = getAuthHeaders();
        const retryResponse = await fetch(`${API_BASE_URL}${url}`, {
          ...options,
          credentials: "include",
          headers: {
            ...retryHeaders,
            ...options.headers,
          },
        });

        return retryResponse;
      }

      // Refresh failed, logout user
      localStorage.removeItem("user");
      localStorage.removeItem("authData");
      window.location.reload();
    }

    return response;
  },

  async get(url, options = {}) {
    return this.fetch(url, { ...options, method: "GET" });
  },

  async post(url, body, options = {}) {
    return this.fetch(url, {
      ...options,
      method: "POST",
      body: JSON.stringify(body),
    });
  },

  async put(url, body, options = {}) {
    return this.fetch(url, {
      ...options,
      method: "PUT",
      body: JSON.stringify(body),
    });
  },

  async delete(url, options = {}) {
    return this.fetch(url, { ...options, method: "DELETE" });
  },
};
