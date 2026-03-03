const API_BASE_URL = import.meta.env.VITE_API_BASE_URL;

export const authService = {
  async register(username, email, password) {
    const response = await fetch(`${API_BASE_URL}/auth/register`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        username,
        email,
        password,
      }),
    });

    if (!response.ok) {
      const error = await response
        .json()
        .catch(() => ({ message: "Registration failed" }));
      throw new Error(error.message || "Registration failed");
    }

    return response.json();
  },

  async login(email, password) {
    const response = await fetch(`${API_BASE_URL}/auth/login`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        usernameOrEmail: email,
        password,
      }),
    });

    if (!response.ok) {
      const error = await response
        .json()
        .catch(() => ({ message: "Login failed" }));
      throw new Error(error.message || "Login failed");
    }

    return response.json();
  },

  async refreshToken(refreshToken) {
    const response = await fetch(`${API_BASE_URL}/auth/refresh`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        refreshToken,
      }),
    });

    if (!response.ok) {
      const error = await response
        .json()
        .catch(() => ({ message: "Token refresh failed" }));
      throw new Error(error.message || "Token refresh failed");
    }

    return response.json();
  },

  async logout(refreshToken) {
    try {
      const response = await fetch(`${API_BASE_URL}/auth/logout`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          refreshToken,
        }),
      });

      if (!response.ok) {
        console.error("Logout API call failed");
      }
    } catch (error) {
      console.error("Logout error:", error);
      // Continue with local cleanup even if API call fails
    }
  },

  getAccessToken() {
    const authData = localStorage.getItem("authData");
    if (authData) {
      const parsed = JSON.parse(authData);
      return parsed.accessToken;
    }
    return null;
  },

  getRefreshToken() {
    const authData = localStorage.getItem("authData");
    if (authData) {
      const parsed = JSON.parse(authData);
      return parsed.refreshToken;
    }
    return null;
  },
};
