// The vessels API client — thin fetch wrappers over /api/vessels and /api/licenses
// (VESSELS.01 § API). Same shape as api.ts for animals: the Vite dev proxy
// forwards /api to the backend, and the mock user goes in the Authorization header.
import type { OwnerInfo, QueryResult } from './api';

export type { OwnerInfo, QueryResult };

export interface Vessel {
  id: number;
  registryCode: string;
  name: string;
  typeCode: string;
  buildYear?: number | null;
  homePort?: string | null;
  grossTonnage?: number | null;
  /** Estonian personal code — a string of 11 digits, never a number. */
  ownerIsikukood?: string | null;
  /** Retirement fields — set only on retired vessels. */
  retiredOn?: string | null;
  retirementReason?: string | null;
  retirementNote?: string | null;
  /** Platform columns, filled by the database trigger; read-only. 'A' = active, 'D' = retired. */
  sysStatus?: string;
  sysVersion?: number;
  sysCreatedAt?: string;
  sysCreatedBy?: string;
  sysModifiedAt?: string;
  sysModifiedBy?: string;
}

/** The body of POST / PUT /api/vessels. */
export type VesselInput = Pick<
  Vessel,
  'registryCode' | 'name' | 'typeCode' | 'buildYear' | 'homePort' | 'grossTonnage' | 'ownerIsikukood'
>;

/** Derived on the server from the dates and the stored status — never stored. */
export type LicenseEffectiveStatus = 'REVOKED' | 'NOT_YET_VALID' | 'EXPIRED' | 'EXPIRING_SOON' | 'VALID';

export interface License {
  id: number;
  vesselId: number;
  licenseNumber: string;
  licenseType: string;
  issuedOn: string;
  validUntil: string;
  /** Stored status: ACTIVE or REVOKED. */
  status: string;
  revokedOn?: string | null;
  revocationReason?: string | null;
  effectiveStatus: LicenseEffectiveStatus;
}

export interface LicenseInput {
  licenseNumber: string;
  licenseType: string;
  issuedOn: string;
  validUntil: string;
}

export interface ExpiringLicense {
  vesselId: number;
  registryCode: string;
  vesselName: string;
  licenseId: number;
  licenseNumber: string;
  licenseType: string;
  issuedOn: string;
  validUntil: string;
  effectiveStatus: LicenseEffectiveStatus;
}

export interface OwnershipPeriod {
  id: number;
  vesselId: number;
  ownerIsikukood: string;
  ownedFrom: string;
  /** Null = the current owner. */
  ownedUntil?: string | null;
}

export interface CodeName {
  code: string;
  name: string;
}

/** VALID and EXPIRING_SOON both count as currently valid (VESSELS.01). */
export const isCurrentlyValid = (status: LicenseEffectiveStatus) =>
  status === 'VALID' || status === 'EXPIRING_SOON';

/** A vessel is retired when it carries a retirement date (and is soft-deleted). */
export const isRetired = (vessel: Pick<Vessel, 'retiredOn' | 'sysStatus'>) =>
  Boolean(vessel.retiredOn) || vessel.sysStatus === 'D';

function authHeaders(): Record<string, string> {
  // MockAuthProvider persists the signed-in mock user here (same as api.ts).
  const user = localStorage.getItem('helex_mock_user') || 'superadmin';
  return { Authorization: `Bearer ${user}` };
}

const jsonHeaders = () => ({ ...authHeaders(), 'Content-Type': 'application/json' });

async function handle<T>(response: Response): Promise<T> {
  if (!response.ok) {
    // The backend answers application/problem+json — surface its human text.
    const problem = await response.json().catch(() => null);
    throw new Error(problem?.detail || problem?.title || `HTTP ${response.status}`);
  }
  return response.json();
}

/** For 204 No Content answers. */
async function handleEmpty(response: Response): Promise<void> {
  if (!response.ok) await handle<unknown>(response);
}

const get = <T>(url: string) => fetch(url, { headers: authHeaders() }).then((r) => handle<T>(r));

const query = (params: Record<string, string | number | boolean | undefined>) =>
  Object.entries(params)
    .filter(([, v]) => v !== undefined && v !== '')
    .map(([k, v]) => `${k}=${encodeURIComponent(String(v))}`)
    .join('&');

export const vesselsApi = {
  list: (params: { textContains?: string; typeCode?: string; retired?: boolean }): Promise<QueryResult<Vessel>> =>
    get(`/api/vessels?${query({ limit: 100, ...params })}`),

  get: (id: number): Promise<Vessel> => get(`/api/vessels/${id}`),

  create: (body: VesselInput): Promise<Vessel> =>
    fetch('/api/vessels', { method: 'POST', headers: jsonHeaders(), body: JSON.stringify(body) }).then((r) =>
      handle<Vessel>(r),
    ),

  /** PUT — the registry code is immutable; the owner can only be set while there is none. */
  update: (id: number, body: VesselInput): Promise<Vessel> =>
    fetch(`/api/vessels/${id}`, { method: 'PUT', headers: jsonHeaders(), body: JSON.stringify(body) }).then((r) =>
      handle<Vessel>(r),
    ),

  /** DELETE with query parameters — a recorded retirement (soft delete, 204). Revokes valid licenses. */
  retire: (id: number, body: { retiredOn: string; reason: string; note?: string }): Promise<void> =>
    fetch(`/api/vessels/${id}?${query(body)}`, { method: 'DELETE', headers: authHeaders() }).then(handleEmpty),

  /** Current owner, live from the population registry. Fails open: 502 here only. */
  owner: (id: number): Promise<OwnerInfo> => get(`/api/vessels/${id}/owner`),

  types: (): Promise<CodeName[]> => get('/api/vessels/types'),

  retirementReasons: (): Promise<CodeName[]> => get('/api/vessels/retirement-reasons'),

  // --- licenses of one vessel ---
  licenses: (vesselId: number): Promise<License[]> => get(`/api/vessels/${vesselId}/licenses`),

  addLicense: (vesselId: number, body: LicenseInput): Promise<License> =>
    fetch(`/api/vessels/${vesselId}/licenses`, {
      method: 'POST',
      headers: jsonHeaders(),
      body: JSON.stringify(body),
    }).then((r) => handle<License>(r)),

  revokeLicense: (vesselId: number, licenseId: number, body: { revokedOn: string; reason: string }): Promise<License> =>
    fetch(`/api/vessels/${vesselId}/licenses/${licenseId}/revocation`, {
      method: 'POST',
      headers: jsonHeaders(),
      body: JSON.stringify(body),
    }).then((r) => handle<License>(r)),

  /** Only a NOT_YET_VALID (scheduled) license can be deleted. */
  deleteLicense: (vesselId: number, licenseId: number): Promise<void> =>
    fetch(`/api/vessels/${vesselId}/licenses/${licenseId}`, { method: 'DELETE', headers: authHeaders() }).then(
      handleEmpty,
    ),

  // --- ownership ---
  ownership: (vesselId: number): Promise<OwnershipPeriod[]> => get(`/api/vessels/${vesselId}/ownership`),

  ownerOfPeriod: (vesselId: number, ownershipId: number): Promise<OwnerInfo> =>
    get(`/api/vessels/${vesselId}/ownership/${ownershipId}/owner`),

  /** Fails closed: the new code is verified with the registry first; registry down → 502, nothing saved. */
  transfer: (vesselId: number, body: { newOwnerIsikukood: string; transferDate: string }): Promise<OwnershipPeriod> =>
    fetch(`/api/vessels/${vesselId}/ownership`, {
      method: 'POST',
      headers: jsonHeaders(),
      body: JSON.stringify(body),
    }).then((r) => handle<OwnershipPeriod>(r)),

  // --- register-wide licenses ---
  expiring: (params: { withinDays?: number; licenseType?: string }): Promise<QueryResult<ExpiringLicense>> =>
    get(`/api/licenses/expiring?${query({ limit: 200, ...params })}`),

  licenseTypes: (): Promise<CodeName[]> => get('/api/licenses/types'),
};
