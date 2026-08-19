import { useEffect } from "react";
import { Navigate, Route, Routes } from "react-router-dom";
import { useAuthStore } from "./store/authStore";
import LoginPage from "./pages/LoginPage";
import SignupPage from "./pages/SignupPage";
import WorkspacePickerPage from "./pages/WorkspacePickerPage";
import WorkspaceShellPage from "./pages/WorkspaceShellPage";
import ChannelPage from "./pages/ChannelPage";
import DMPage from "./pages/DMPage";
import EmptyThreadPage from "./pages/EmptyThreadPage";

function RequireAuth({ children }: { children: React.ReactElement }) {
  const { user, status } = useAuthStore();
  if (status !== "ready") return <FullScreenLoader />;
  if (!user) return <Navigate to="/login" replace />;
  return children;
}

function FullScreenLoader() {
  return (
    <div className="flex h-full items-center justify-center text-slate-500">読み込み中...</div>
  );
}

export default function App() {
  const init = useAuthStore((s) => s.init);
  const status = useAuthStore((s) => s.status);

  useEffect(() => {
    init();
  }, [init]);

  if (status === "idle" || status === "loading") return <FullScreenLoader />;

  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/signup" element={<SignupPage />} />
      <Route
        path="/"
        element={
          <RequireAuth>
            <WorkspacePickerPage />
          </RequireAuth>
        }
      />
      <Route
        path="/w/:workspaceId"
        element={
          <RequireAuth>
            <WorkspaceShellPage />
          </RequireAuth>
        }
      >
        <Route index element={<EmptyThreadPage />} />
        <Route path="c/:channelId" element={<ChannelPage />} />
        <Route path="dm/:dmId" element={<DMPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
