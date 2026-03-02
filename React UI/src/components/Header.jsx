import { useAuth } from "../contexts/AuthContext";
import { useTheme } from "../contexts/ThemeContext";
import styles from "./Header.module.css";

const Header = () => {
  const { user, logout } = useAuth();
  const { theme, toggleTheme } = useTheme();

  return (
    <header className={styles.header}>
      <div className={styles.logo}>
        <h2>DropBox</h2>
      </div>

      <div className={styles.actions}>
        <button
          onClick={toggleTheme}
          className={styles.themeBtn}
          title="Toggle theme"
        >
          {theme === "light" ? "🌙" : "☀️"}
        </button>

        <div className={styles.user}>
          <span>{user?.username || user?.name || user?.email}</span>
          <button onClick={logout} className={styles.logoutBtn}>
            Logout
          </button>
        </div>
      </div>
    </header>
  );
};

export default Header;
