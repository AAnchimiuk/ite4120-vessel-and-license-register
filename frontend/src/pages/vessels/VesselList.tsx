import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Select } from 'antd';
import { ClockCircleOutlined, PlusOutlined } from '@ant-design/icons';
import {
  AppButtonPrimary,
  AppButtonSecondary,
  AppLink,
  AppTag,
  ResourceList,
  useAppNotification,
  type ResourceListColumn,
} from '@helex/ui';
import { vesselsApi, type CodeName, type OwnerInfo, type Vessel } from '../../vesselsApi';
import { VesselStatusTag } from './LicenseStatusTag';

type StatusFilter = 'active' | 'retired';

/**
 * The vessel register (VESSELS.01 § UI › Vessel list) — ResourceList with search
 * by code or name, filters for vessel type and Active / Retired, and a detail
 * panel showing the owner fetched live through the registry adapter.
 */
export const VesselList = () => {
  const navigate = useNavigate();
  const notify = useAppNotification();
  const [vessels, setVessels] = useState<Vessel[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [selected, setSelected] = useState<Vessel | null>(null);
  const [owner, setOwner] = useState<OwnerInfo | null>(null);
  const [ownerUnavailable, setOwnerUnavailable] = useState(false);
  const [types, setTypes] = useState<CodeName[]>([]);
  /* What the filter Selects show vs what the list is filtered by — they meet
     when the user presses Apply (ResourceList's filter card works that way). */
  const [typeFilter, setTypeFilter] = useState<string | undefined>();
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('active');
  const [applied, setApplied] = useState<{ typeCode?: string; status: StatusFilter }>({ status: 'active' });

  useEffect(() => {
    vesselsApi.types().then(setTypes).catch(() => setTypes([]));
  }, []);

  const load = useCallback(
    (text: string, typeCode: string | undefined, status: StatusFilter) => {
      setLoading(true);
      vesselsApi
        .list({ textContains: text || undefined, typeCode, retired: status === 'retired' })
        .then((result) => setVessels(result.data))
        .catch((e) => notify.error('Could not load vessels', e.message))
        .finally(() => setLoading(false));
    },
    [notify],
  );

  useEffect(() => {
    const t = setTimeout(() => load(search, applied.typeCode, applied.status), 250);
    return () => clearTimeout(t);
  }, [search, applied, load]);

  useEffect(() => {
    setOwner(null);
    setOwnerUnavailable(false);
    if (selected?.id && selected.ownerIsikukood) {
      vesselsApi
        .owner(selected.id)
        .then(setOwner)
        .catch(() => setOwnerUnavailable(true)); // fail open: say so, never block the page
    }
  }, [selected]);

  const typeName = (code: string) => types.find((t) => t.code === code)?.name ?? code;

  const columns: ResourceListColumn<Vessel>[] = [
    {
      key: 'registryCode',
      title: 'Registry code',
      dataIndex: 'registryCode',
      locked: true,
      // The identifier is the way into the record: it links to the vessel's page.
      render: (code: string, vessel: Vessel) => (
        <AppLink
          href={`/vessels/${vessel.id}`}
          onClick={(e) => {
            e.preventDefault();
            e.stopPropagation();
            navigate(`/vessels/${vessel.id}`);
          }}
        >
          {code}
        </AppLink>
      ),
    },
    { key: 'name', title: 'Name', dataIndex: 'name' },
    {
      key: 'typeCode',
      title: 'Type',
      dataIndex: 'typeCode',
      render: (code: string) => <AppTag>{typeName(code)}</AppTag>,
    },
    { key: 'homePort', title: 'Home port', dataIndex: 'homePort' },
    { key: 'buildYear', title: 'Built', dataIndex: 'buildYear' },
    {
      key: 'status',
      title: 'Status',
      dataIndex: 'retiredOn',
      render: (retiredOn: string | null) => <VesselStatusTag retiredOn={retiredOn} />,
    },
    { key: 'grossTonnage', title: 'Gross tonnage', dataIndex: 'grossTonnage', defaultVisible: false },
  ];

  return (
    <ResourceList<Vessel>
      title={applied.status === 'retired' ? 'Vessels — retired' : 'Vessels'}
      columns={columns}
      dataSource={vessels}
      rowKey="id"
      loading={loading}
      search={{ value: search, onChange: setSearch, placeholder: 'Name or registry code…' }}
      filter={{
        content: (
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            <Select
              style={{ minWidth: 200 }}
              placeholder="Vessel type"
              allowClear
              value={typeFilter}
              onChange={setTypeFilter}
              options={types.map((t) => ({ value: t.code, label: t.name }))}
            />
            <Select<StatusFilter>
              style={{ minWidth: 160 }}
              value={statusFilter}
              onChange={setStatusFilter}
              options={[
                { value: 'active', label: 'Active' },
                { value: 'retired', label: 'Retired' },
              ]}
            />
          </div>
        ),
        onApply: () => setApplied({ typeCode: typeFilter, status: statusFilter }),
        onClear: () => {
          setTypeFilter(undefined);
          setStatusFilter('active');
          setApplied({ status: 'active' });
        },
      }}
      actions={
        <div style={{ display: 'flex', gap: 8 }}>
          <AppButtonSecondary icon={<ClockCircleOutlined />} onClick={() => navigate('/licenses/expiring')}>
            Expiring licenses
          </AppButtonSecondary>
          <AppButtonPrimary icon={<PlusOutlined />} onClick={() => navigate('/vessels/new')}>
            Register vessel
          </AppButtonPrimary>
        </div>
      }
      detailView={{
        selectedRecord: selected,
        onSelectedRecordChange: setSelected,
        title: 'Vessel',
        render: (vessel) => (
          <div style={{ display: 'grid', gap: 8 }}>
            <div>
              <b>{vessel.name}</b> · {vessel.registryCode}
            </div>
            <div>
              Type: {typeName(vessel.typeCode)} · <VesselStatusTag retiredOn={vessel.retiredOn} />
            </div>
            {vessel.homePort && <div>Home port: {vessel.homePort}</div>}
            {vessel.buildYear && <div>Built: {vessel.buildYear}</div>}
            {vessel.ownerIsikukood && (
              <div>
                Owner: {vessel.ownerIsikukood}
                {owner && (
                  <>
                    {' — '}
                    <b>
                      {owner.firstName} {owner.lastName}
                    </b>
                    , {owner.address}
                    <div style={{ opacity: 0.65, fontSize: 12 }}>via the owner-registry adapter (mock / X-Road)</div>
                  </>
                )}
                {ownerUnavailable && (
                  <div style={{ opacity: 0.65, fontSize: 12 }}>Owner details temporarily unavailable</div>
                )}
              </div>
            )}
          </div>
        ),
      }}
    />
  );
};
