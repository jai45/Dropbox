import { useAuth } from "./contexts/AuthContext";
import Login from "./components/Login";
import Dashboard from "./components/Dashboard";
import "./App.css";

function App() {
  const { user } = useAuth();

  return user ? <Dashboard /> : <Login />;
}

export default App;
