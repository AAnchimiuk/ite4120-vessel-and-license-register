import type { ReactElement } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import { AnimalList } from './pages/AnimalList';
import { AnimalCreate } from './pages/AnimalCreate';
import { AnimalDetail } from './pages/AnimalDetail';
import { VesselList } from './pages/vessels/VesselList';
import { VesselCreate } from './pages/vessels/VesselCreate';
import { VesselDetail } from './pages/vessels/VesselDetail';
import { ExpiringLicenses } from './pages/licenses/ExpiringLicenses';

/**
 * Components that may be ABSENT from this checkout register their routes by
 * exporting `routes: ReactElement[]` (a list of <Route>s) from
 * `pages/<component>/routes.tsx`. The glob is resolved at build time and is
 * simply empty when no such folder exists — the lecturer's private library
 * module lives there, gitignored. Students: add your <Route>s below, like animals.
 */
const componentRoutes = Object.values(
  import.meta.glob<{ routes: ReactElement[] }>('./pages/*/routes.tsx', { eager: true }),
).flatMap((m) => m.routes);

export const App = () => (
  <Routes>
    <Route path="/" element={<Navigate to="/vessels" replace />} />
    {/* Our component — Vessel and License Register (VESSELS.01 § UI). */}
    <Route path="/vessels" element={<VesselList />} />
    <Route path="/vessels/new" element={<VesselCreate />} />
    <Route path="/vessels/:id" element={<VesselDetail />} />
    <Route path="/licenses/expiring" element={<ExpiringLicenses />} />
    {/* The course template's worked example, kept for reference. */}
    <Route path="/animals" element={<AnimalList />} />
    <Route path="/animals/new" element={<AnimalCreate />} />
    <Route path="/animals/:id" element={<AnimalDetail />} />
    {componentRoutes}
  </Routes>
);
