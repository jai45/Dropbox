/* eslint-disable react-refresh/only-export-components */
import { createContext, useContext, useState } from "react";
import { authService } from "../services/authService";

const AuthContext = createContext();

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within AuthProvider");
  }
  return context;
};

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(() => {
    const savedUser = localStorage.getItem("user");
    return savedUser ? JSON.parse(savedUser) : null;
  });

  const login = async (email, password) => {
    const response = await authService.login(email, password);

    const userData = {
      userId: response.userId,
      username: response.username,
      email: response.email,
    };

    const authData = {
      accessToken: response.accessToken,
      refreshToken: response.refreshToken,
      accessExpiresIn: response.accessExpiresIn,
      expiresAt: Date.now() + response.accessExpiresIn * 1000,
    };

    setUser(userData);
    localStorage.setItem("user", JSON.stringify(userData));
    localStorage.setItem("authData", JSON.stringify(authData));

    return true;
  };

  const register = async (username, email, password) => {
    const response = await authService.register(username, email, password);

    const userData = {
      userId: response.userId,
      username: response.username,
      email: response.email,
    };

    const authData = {
      accessToken: response.accessToken,
      refreshToken: response.refreshToken,
      accessExpiresIn: response.accessExpiresIn,
      expiresAt: Date.now() + response.accessExpiresIn * 1000,
    };

    setUser(userData);
    localStorage.setItem("user", JSON.stringify(userData));
    localStorage.setItem("authData", JSON.stringify(authData));

    return true;
  };

  const logout = async () => {
    try {
      const refreshToken = authService.getRefreshToken();
      if (refreshToken) {
        await authService.logout(refreshToken);
      }
    } catch (error) {
      console.error("Logout error:", error);
    } finally {
      // Always clear local data
      setUser(null);
      localStorage.removeItem("user");
      localStorage.removeItem("authData");
    }
  };

  const refreshAccessToken = async () => {
    try {
      const refreshToken = authService.getRefreshToken();
      if (!refreshToken) {
        logout();
        return null;
      }

      const response = await authService.refreshToken(refreshToken);

      const authData = {
        accessToken: response.accessToken,
        refreshToken: response.refreshToken,
        accessExpiresIn: response.accessExpiresIn,
        expiresAt: Date.now() + response.accessExpiresIn * 1000,
      };

      localStorage.setItem("authData", JSON.stringify(authData));
      return response.accessToken;
    } catch (error) {
      console.error("Token refresh failed:", error);
      logout();
      return null;
    }
  };

  return (
    <AuthContext.Provider
      value={{ user, login, register, logout, refreshAccessToken }}
    >
      {children}
    </AuthContext.Provider>
  );
};
