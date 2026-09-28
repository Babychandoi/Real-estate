import React from 'react';
import ReactDOM from 'react-dom/client';
import { RouterProvider } from 'react-router-dom';
import { router } from './routes';
// Imported for its module-load side effect (m4): captures the landing UTM before any route effect can rewrite
// the URL and drop it (e.g. the search page replacing the query string, a listing page redirecting to its slug).
import './shared/analytics/track';
import { routePattern, startRumWhenConsented } from './shared/analytics/rum';
import { resetPrerenderedHeadOnNavigation } from './shared/seo/prerenderHead';
import { ToastProvider } from './shared/ui/Toast';
import './styles/index.css';

// Before RouterProvider subscribes: the landing page's prerendered head is reset before the next route renders.
resetPrerenderedHeadOnNavigation(router);

// Core Web Vitals (F15.3): only after analytics consent and for a sample of sessions; web-vitals loads on demand.
startRumWhenConsented({ route: () => routePattern(router.state.matches) });

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ToastProvider>
      <RouterProvider router={router} />
    </ToastProvider>
  </React.StrictMode>,
);
