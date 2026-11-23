import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { InputNumber, Select } from 'antd';
import { UnorderedListOutlined } from '@ant-design/icons';
import {
  AppButtonSecondary,
  AppLink,
  AppTag,
  ResourceList,
  useAppNotification,
  type ResourceListColumn,
} from '@helex/ui';
import { vesselsApi, type CodeName, type ExpiringLicense } from '../../vesselsApi';
import { LicenseStatusTag } from '../vessels/LicenseStatusTag';

const DEFAULT_DAYS = 30;

/**
 * Expiring licenses (VESSELS.01 § UI › Expiring licenses): every license expiring
 * within N days (default 30, 1–365) on an active vessel, soonest first — the
 * server sorts and excludes retired vessels. Filters: "within days" and license type.
 */
export const ExpiringLicenses = () => {
  const navigate = useNavigate();
  const notify = useAppNotification();
  const [rows, setRows] = useState<ExpiringLicense[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [types, setTypes] = useState<CodeName[]>([]);
  const [daysFilter, setDaysFilter] = useState<number>(DEFAULT_DAYS);
  const [typeFilter, setTypeFilter] = useState<string | undefined>();
  const [applied, setApplied] = useState<{ withinDays: number; licenseType?: string }>({
    withinDays: DEFAULT_DAYS,
  });

  useEffect(() => {
    vesselsApi.licenseTypes().then(setTypes).catch(() => setTypes([]));
  }, []);

  const load = useCallback(
    (params: { withinDays: number; licenseType?: string }) => {
      setLoading(true);
      vesselsApi
        .expiring(params)
        .then((result) => setRows(result.data))
        .catch((e) => notify.error('Could not load expiring licenses', e.message))
        .finally(() => setLoading(false));
    },
    [notify],
  );

  useEffect(() => load(applied), [applied, load]);

  // Search narrows the loaded rows by vessel name, registry code or license number.
  const needle = search.trim().toLowerCase();
  const visible = needle
    ? rows.filter((r) =>
        [r.vesselName, r.registryCode, r.licenseNumber].some((v) => v?.toLowerCase().includes(needle)),
      )
    : rows;

  const typeName = (code: string) => types.find((t) => t.code === code)?.name ?? code;

  const columns: ResourceListColumn<ExpiringLicense>[] = [
    {
      key: 'registryCode',
      title: 'Registry code',
      dataIndex: 'registryCode',
      locked: true,
      render: (code: string, row: ExpiringLicense) => (
        <AppLink
          href={`/vessels/${row.vesselId}`}
          onClick={(e) => {
            e.preventDefault();
            e.stopPropagation();
            navigate(`/vessels/${row.vesselId}`);
          }}
        >
          {code}
        </AppLink>
      ),
    },
    { key: 'vesselName', title: 'Vessel', dataIndex: 'vesselName' },
    { key: 'licenseNumber', title: 'License number', dataIndex: 'licenseNumber' },
    {
      key: 'licenseType',
      title: 'Type',
      dataIndex: 'licenseType',
      render: (code: string) => <AppTag>{typeName(code)}</AppTag>,
    },
    { key: 'validUntil', title: 'Valid until', dataIndex: 'validUntil' },
    {
      key: 'effectiveStatus',
      title: 'Status',
      dataIndex: 'effectiveStatus',
      render: (_: unknown, row: ExpiringLicense) => <LicenseStatusTag status={row.effectiveStatus} />,
    },
  ];

  return (
    <ResourceList<ExpiringLicense>
      title={`Licenses expiring within ${applied.withinDays} days`}
      columns={columns}
      dataSource={visible}
      rowKey="licenseId"
      loading={loading}
      search={{ value: search, onChange: setSearch, placeholder: 'Vessel, registry code or license…' }}
      filter={{
        content: (
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
            <span>Within</span>
            <InputNumber
              min={1}
              max={365}
              precision={0}
              value={daysFilter}
              onChange={(n) => setDaysFilter(typeof n === 'number' ? n : DEFAULT_DAYS)}
            />
            <span>days</span>
            <Select
              style={{ minWidth: 200 }}
              placeholder="License type"
              allowClear
              value={typeFilter}
              onChange={setTypeFilter}
              options={types.map((t) => ({ value: t.code, label: t.name }))}
            />
          </div>
        ),
        onApply: () => setApplied({ withinDays: daysFilter, licenseType: typeFilter }),
        onClear: () => {
          setDaysFilter(DEFAULT_DAYS);
          setTypeFilter(undefined);
          setApplied({ withinDays: DEFAULT_DAYS });
        },
      }}
      actions={
        <AppButtonSecondary icon={<UnorderedListOutlined />} onClick={() => navigate('/vessels')}>
          All vessels
        </AppButtonSecondary>
      }
    />
  );
};
