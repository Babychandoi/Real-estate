import { Link } from "react-router-dom";
import { StatePanel } from "./Feedback";
export function RouteFailure() {
  return (
    <main className="ndc-page py-16">
      <StatePanel
        error
        onRetry={() => window.location.reload()}
        action={
          <Link to="/" className="ndc-text-link">
            Về trang chủ
          </Link>
        }
      />
    </main>
  );
}
