/// <reference types="node" />
import { beforeEach, describe, it } from 'node:test';
import assert from 'node:assert/strict';
import {
  isCurrentlyValid,
  isRetired,
  vesselsApi,
  type ExpiringLicense,
  type License,
  type LicenseEffectiveStatus,
} from './vesselsApi.ts';

interface RecordedCall {
  url: string;
  method: string;
  headers: Record<string, string>;
  body?: unknown;
}

describe('vessels frontend domain & API contract tests', () => {
  let calls: RecordedCall[] = [];
  let storage: Record<string, string> = {};
  let nextStatus = 200;
  let nextBody: unknown = {};

  beforeEach(() => {
    calls = [];
    storage = {};
    nextStatus = 200;
    nextBody = {};

    Object.defineProperty(globalThis, 'localStorage', {
      configurable: true,
      value: {
        getItem: (key: string) => storage[key] ?? null,
        setItem: (key: string, val: string) => {
          storage[key] = val;
        },
      },
    });

    Object.defineProperty(globalThis, 'fetch', {
      configurable: true,
      value: async (input: string | URL | Request, init?: RequestInit) => {
        const url = String(input);
        const method = init?.method ?? 'GET';
        const headers = (init?.headers as Record<string, string>) ?? {};
        const body = init?.body ? JSON.parse(String(init.body)) : undefined;
        calls.push({ url, method, headers, body });

        return {
          ok: nextStatus >= 200 && nextStatus < 300,
          status: nextStatus,
          json: async () => nextBody,
        } as Response;
      },
    });
  });

  it('isCurrentlyValid counts only VALID and EXPIRING_SOON as currently valid (VESSELS.01)', () => {
    const validStatuses: LicenseEffectiveStatus[] = ['VALID', 'EXPIRING_SOON'];
    const invalidStatuses: LicenseEffectiveStatus[] = ['EXPIRED', 'NOT_YET_VALID', 'REVOKED'];

    for (const s of validStatuses) {
      assert.equal(isCurrentlyValid(s), true, `${s} must be currently valid`);
    }
    for (const s of invalidStatuses) {
      assert.equal(isCurrentlyValid(s), false, `${s} must not be currently valid`);
    }
  });

  it('isRetired detects retired vessels by retiredOn date or sysStatus = D', () => {
    assert.equal(isRetired({ retiredOn: null, sysStatus: 'A' }), false);
    assert.equal(isRetired({ retiredOn: undefined, sysStatus: undefined }), false);
    assert.equal(isRetired({ retiredOn: '2026-11-20', sysStatus: 'A' }), true);
    assert.equal(isRetired({ retiredOn: null, sysStatus: 'D' }), true);
  });

  it('sends Authorization: Bearer <mock_user> header (defaulting to superadmin)', async () => {
    nextBody = { data: [], meta: { total: 0, offset: 0, itemsPerPage: 100, pages: 0 } };
    await vesselsApi.list({});
    assert.equal(calls[0].headers.Authorization, 'Bearer superadmin');

    storage.helex_mock_user = 'aleksander';
    await vesselsApi.get(42);
    assert.equal(calls[1].url, '/api/vessels/42');
    assert.equal(calls[1].headers.Authorization, 'Bearer aleksander');
  });

  it('vesselsApi.list builds query parameters and omits empty filters', async () => {
    nextBody = { data: [], meta: { total: 0, offset: 0, itemsPerPage: 100, pages: 0 } };
    await vesselsApi.list({ textContains: 'Tuule', typeCode: 'FISHING', retired: false });

    assert.equal(calls.length, 1);
    assert.equal(calls[0].method, 'GET');
    assert.equal(calls[0].url, '/api/vessels?limit=100&textContains=Tuule&typeCode=FISHING&retired=false');
  });

  it('vesselsApi.create (POST) and vesselsApi.update (PUT) send JSON payloads', async () => {
    const payload = {
      registryCode: 'EST-5005',
      name: 'Test Ship',
      typeCode: 'CARGO',
      buildYear: 2020,
      homePort: 'Tallinn',
      grossTonnage: 500,
      ownerIsikukood: '38102130265',
    };
    nextBody = { id: 10, ...payload, sysStatus: 'A' };

    const created = await vesselsApi.create(payload);
    assert.equal(created.id, 10);
    assert.equal(calls[0].method, 'POST');
    assert.equal(calls[0].url, '/api/vessels');
    assert.deepEqual(calls[0].body, payload);

    await vesselsApi.update(10, { ...payload, name: 'Renamed Ship' });
    assert.equal(calls[1].method, 'PUT');
    assert.equal(calls[1].url, '/api/vessels/10');
    assert.equal((calls[1].body as { name: string }).name, 'Renamed Ship');
  });

  it('vesselsApi.retire sends DELETE /api/vessels/{id} (soft delete, 204 No Content) with query parameters', async () => {
    nextStatus = 204;
    await vesselsApi.retire(7, {
      retiredOn: '2026-11-21',
      reason: 'OTHER',
      note: 'Moved to foreign register',
    });

    assert.equal(calls.length, 1);
    assert.equal(calls[0].method, 'DELETE');
    assert.equal(
      calls[0].url,
      '/api/vessels/7?retiredOn=2026-11-21&reason=OTHER&note=Moved%20to%20foreign%20register',
    );
  });

  it('vesselsApi license operations use POST for add/revoke and DELETE for scheduled NOT_YET_VALID licenses', async () => {
    nextBody = {
      id: 99,
      vesselId: 1,
      licenseNumber: 'LIC-N-99',
      licenseType: 'NAVIGATION',
      issuedOn: '2027-01-01',
      validUntil: '2028-01-01',
      status: 'ACTIVE',
      effectiveStatus: 'NOT_YET_VALID',
    };

    await vesselsApi.addLicense(1, {
      licenseNumber: 'LIC-N-99',
      licenseType: 'NAVIGATION',
      issuedOn: '2027-01-01',
      validUntil: '2028-01-01',
    });
    assert.equal(calls[0].method, 'POST');
    assert.equal(calls[0].url, '/api/vessels/1/licenses');

    await vesselsApi.revokeLicense(1, 99, { revokedOn: '2026-11-21', reason: 'Failed inspection' });
    assert.equal(calls[1].method, 'POST');
    assert.equal(calls[1].url, '/api/vessels/1/licenses/99/revocation');

    nextStatus = 204;
    await vesselsApi.deleteLicense(1, 99);
    assert.equal(calls[2].method, 'DELETE');
    assert.equal(calls[2].url, '/api/vessels/1/licenses/99');
  });

  it('vesselsApi ownership & live X-Road lookup endpoints call the expected paths', async () => {
    nextBody = { isikukood: '38102130265', firstName: 'Jaan', lastName: 'Tamm', address: 'Tallinn' };
    const owner = await vesselsApi.owner(1);
    assert.equal(owner.lastName, 'Tamm');
    assert.equal(calls[0].url, '/api/vessels/1/owner');

    await vesselsApi.ownerOfPeriod(1, 5);
    assert.equal(calls[1].url, '/api/vessels/1/ownership/5/owner');

    nextBody = { id: 6, vesselId: 1, ownerIsikukood: '47503121234', ownedFrom: '2026-11-21', ownedUntil: null };
    await vesselsApi.transfer(1, { newOwnerIsikukood: '47503121234', transferDate: '2026-11-21' });
    assert.equal(calls[2].method, 'POST');
    assert.equal(calls[2].url, '/api/vessels/1/ownership');
  });

  it('surfaces RFC 9457 application/problem+json detail verbatim on 400 / 409 / 502 errors', async () => {
    nextStatus = 409;
    nextBody = {
      status: 409,
      title: 'Conflict with current state',
      detail: 'an active vessel with registry code EST-1001 already exists',
    };

    await assert.rejects(
      () =>
        vesselsApi.create({
          registryCode: 'EST-1001',
          name: 'Dup',
          typeCode: 'FISHING',
        }),
      { message: 'an active vessel with registry code EST-1001 already exists' },
    );

    nextStatus = 502;
    nextBody = {
      status: 502,
      title: 'Owner registry unavailable',
      detail: 'owner registry unreachable: Connection refused',
    };

    await assert.rejects(() => vesselsApi.owner(1), {
      message: 'owner registry unreachable: Connection refused',
    });
  });

  it('handles 400 validation errors across ownership transfer, license issuance, and vessel retirement (DELETE)', async () => {
    nextStatus = 400;
    nextBody = {
      status: 400,
      title: 'Invalid request',
      detail: 'person 11111111111 not found in the population registry',
    };
    await assert.rejects(
      () => vesselsApi.transfer(1, { newOwnerIsikukood: '11111111111', transferDate: '2026-11-23' }),
      { message: 'person 11111111111 not found in the population registry' },
    );

    nextBody = {
      status: 400,
      title: 'Invalid request',
      detail: 'validUntil must not precede issuedOn',
    };
    await assert.rejects(
      () =>
        vesselsApi.addLicense(1, {
          licenseNumber: 'LIC-BAD',
          licenseType: 'NAVIGATION',
          issuedOn: '2026-12-01',
          validUntil: '2026-11-01',
        }),
      { message: 'validUntil must not precede issuedOn' },
    );

    nextBody = {
      status: 400,
      title: 'Invalid request',
      detail: 'retirementNote is required when reason is OTHER',
    };
    await assert.rejects(
      () => vesselsApi.retire(1, { retiredOn: '2026-11-23', reason: 'OTHER' }),
      { message: 'retirementNote is required when reason is OTHER' },
    );
  });

  it('handles 409 Conflict on DELETE /api/vessels/{id}/licenses/{licenseId} and DELETE /api/vessels/{id}', async () => {
    nextStatus = 409;
    nextBody = {
      status: 409,
      title: 'Conflict with current state',
      detail: 'only a license that is NOT_YET_VALID can be deleted (current status: VALID)',
    };
    await assert.rejects(() => vesselsApi.deleteLicense(1, 10), {
      message: 'only a license that is NOT_YET_VALID can be deleted (current status: VALID)',
    });

    nextBody = {
      status: 409,
      title: 'Conflict with current state',
      detail: 'vessel 1 is already retired',
    };
    await assert.rejects(
      () => vesselsApi.retire(1, { retiredOn: '2026-11-23', reason: 'SCRAPPED' }),
      { message: 'vessel 1 is already retired' },
    );
  });

  it('falls back gracefully to problem.title or HTTP status code when detail is absent or body is non-JSON', async () => {
    nextStatus = 404;
    nextBody = { status: 404, title: 'Resource not found' };
    await assert.rejects(() => vesselsApi.get(99999), {
      message: 'Resource not found',
    });

    // Simulate non-JSON gateway/proxy error where response.json() rejects
    Object.defineProperty(globalThis, 'fetch', {
      configurable: true,
      value: async () =>
        ({
          ok: false,
          status: 503,
          json: async () => {
            throw new SyntaxError('Unexpected token < in JSON');
          },
        }) as unknown as Response,
    });

    await assert.rejects(() => vesselsApi.list({}), {
      message: 'HTTP 503',
    });
  });

  it('license compliance summary and expiring list search filter match UI specification rules', () => {
    const licenses: License[] = [
      {
        id: 1,
        vesselId: 1,
        licenseNumber: 'L-1',
        licenseType: 'NAVIGATION',
        issuedOn: '2025-01-01',
        validUntil: '2026-11-30',
        status: 'ACTIVE',
        effectiveStatus: 'EXPIRING_SOON',
      },
      {
        id: 2,
        vesselId: 1,
        licenseNumber: 'L-2',
        licenseType: 'FISHING',
        issuedOn: '2024-01-01',
        validUntil: '2025-01-01',
        status: 'ACTIVE',
        effectiveStatus: 'EXPIRED',
      },
      {
        id: 3,
        vesselId: 1,
        licenseNumber: 'L-3',
        licenseType: 'SAFETY',
        issuedOn: '2025-01-01',
        validUntil: '2027-01-01',
        status: 'REVOKED',
        effectiveStatus: 'REVOKED',
      },
    ];

    const typesIssued = Array.from(new Set(licenses.map((l) => l.licenseType)));
    const compliance = Object.fromEntries(
      typesIssued.map((type) => [
        type,
        licenses.some((l) => l.licenseType === type && isCurrentlyValid(l.effectiveStatus)),
      ]),
    );

    assert.equal(compliance.NAVIGATION, true);
    assert.equal(compliance.FISHING, false);
    assert.equal(compliance.SAFETY, false);

    const rows: ExpiringLicense[] = [
      {
        vesselId: 1,
        registryCode: 'EST-1001',
        vesselName: 'Tuulelind',
        licenseId: 10,
        licenseNumber: 'LIC-N-102',
        licenseType: 'NAVIGATION',
        issuedOn: '2025-11-01',
        validUntil: '2026-11-30',
        effectiveStatus: 'EXPIRING_SOON',
      },
    ];
    const needle = 'tuule';
    const filtered = rows.filter((r) =>
      [r.vesselName, r.registryCode, r.licenseNumber].some((v) => v?.toLowerCase().includes(needle)),
    );
    assert.equal(filtered.length, 1);
  });
});
