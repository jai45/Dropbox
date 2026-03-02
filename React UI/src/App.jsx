import { useState } from "react";
import { useAuth } from "./contexts/AuthContext";
import Login from "./components/Login";
import Signup from "./components/Signup";
import Dashboard from "./components/Dashboard";
import "./App.css";

function App() {
  const { user } = useAuth();
  const [showSignup, setShowSignup] = useState(false);

  if (user) {
    return <Dashboard />;
  }

  return showSignup ? (
    <Signup onSwitchToLogin={() => setShowSignup(false)} />
  ) : (
    <Login onSwitchToSignup={() => setShowSignup(true)} />
  );
}

export default App;
