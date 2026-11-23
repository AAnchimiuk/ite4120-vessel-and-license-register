import { useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { Alert, Button, Form, InputNumber } from 'antd';
import { ContainerOutlined, StopOutlined } from '@ant-design/icons';
import { useDataController } from '@helex/core';
import {
  AppCard,
  AppTag,
  AppTimestamp,
  FieldItem,
  ResourceForm,
  useAppNotification,
  type ResourceFormSection,
} from '@helex/ui';
import { isRetired, vesselsApi, type CodeName, type OwnerInfo, type Vessel } from '../../vesselsApi';
import { VesselStatusTag } from './LicenseStatusTag';
import { LicensesCard } from './LicensesCard';
import { OwnershipCard } from './OwnershipCard';
import { RetireModal } from './RetireModal';

/** A number input for ResourceForm's `custom` field: the form store holds a number or null. */
const NumberField = ({
  value,
  onChange,
  min,
  max,
  precision,
}: {
  value?: unknown;
  onChange?: (next: number | null) => void;
  min?: number;
  max?: number;
  precision?: number;
}) => (
  <InputNumber
    style={{ width: '100%' }}
    value={value === null || value === undefined || value === '' ? null : Number(value)}
    onChange={(n) => onChange?.(n === null || n === undefined ? null : Number(n))}
    min={min}
    max={max}
    precision={precision}
  />
);

/**
 * One vessel (VESSELS.01 § UI › Vessel page) on @helex/ui's ResourceForm +
 * useDataController, like AnimalDetail. View mode reads the record; Edit → Save is
 * `PUT /api/vessels/{id}` (registry code is identity, owner code is read-only once
 * set — ownership changes go through the transfer). "Retire" opens the retirement
 * modal. Below the form: licenses (with compliance summary) and ownership history.
 * A retired vessel shows a banner and every changing action is hidden.
 */
export const VesselDetail = () => {
  const { id } = useParams();
  const vesselId = Number(id);
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const notify = useAppNotification();
  const [form] = Form.useForm<Vessel>();
  const dc = useDataController<Vessel>({} as Vessel);
  const [mode, setMode] = useState<'view' | 'edit'>(searchParams.get('edit') === '1' ? 'edit' : 'view');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [types, setTypes] = useState<CodeName[]>([]);
  const [reasons, setReasons] = useState<CodeName[]>([]);
  const [owner, setOwner] = useState<OwnerInfo | null>(null);
  const [ownerUnavailable, setOwnerUnavailable] = useState(false);
  const [retireOpen, setRetireOpen] = useState(false);
  /* Bumped after a retirement or transfer so the licenses / ownership cards reload. */
  const [refreshKey, setRefreshKey] = useState(0);

  const loadOwner = useCallback((vessel: Vessel) => {
    setOwner(null);
    setOwnerUnavailable(false);
    if (vessel.ownerIsikukood) {
      vesselsApi
        .owner(vessel.id)
        .then(setOwner)
        .catch(() => setOwnerUnavailable(true)); // fail open: the vessel stays usable
    }
  }, []);

  const loadVessel = useCallback(() => {
    return vesselsApi.get(vesselId).then((vessel) => {
      dc.load(vessel);
      form.setFieldsValue(vessel);
      loadOwner(vessel);
      if (isRetired(vessel)) {
        setMode('view');
      } else if (searchParams.get('edit') === '1') {
        setMode('edit');
      }
      return vessel;
    });
    // dc and form are stable handles; the record is keyed by the route parameter.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [vesselId, loadOwner]);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    Promise.all([loadVessel(), vesselsApi.types(), vesselsApi.retirementReasons()])
      .then(([, typeList, reasonList]) => {
        if (cancelled) return;
        setTypes(typeList);
        setReasons(reasonList);
      })
      .catch((e) => {
        notify.error('Could not load the vessel', e.message);
        navigate('/vessels');
      })
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
    // navigate is a stable handle.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loadVessel, notify]);

  const vessel = dc.current;
  const retired = isRetired(vessel);
  const typeName = (code?: string) => types.find((t) => t.code === code)?.name ?? code ?? '';
  const reasonName = (code?: string | null) => reasons.find((r) => r.code === code)?.name ?? code ?? '';

  const save = async (values: Vessel) => {
    setSaving(true);
    try {
      const updated = await vesselsApi.update(vesselId, {
        registryCode: vessel.registryCode, // immutable identity
        name: values.name,
        typeCode: values.typeCode,
        buildYear: values.buildYear ?? null,
        homePort: values.homePort || null,
        grossTonnage: values.grossTonnage ?? null,
        // Once set, the owner only changes through a transfer; before that it may be set here.
        ownerIsikukood: vessel.ownerIsikukood || values.ownerIsikukood || null,
      });
      dc.load(updated);
      form.setFieldsValue(updated);
      loadOwner(updated);
      setMode('view');
      if (searchParams.has('edit')) setSearchParams({}, { replace: true });
      setRefreshKey((k) => k + 1); // setting a first owner opens an ownership period
      notify.success('Saved', `${updated.name} · ${updated.registryCode}`);
    } catch (e) {
      notify.error('Save failed', (e as Error).message); // 400 / 409 problem detail, verbatim
    } finally {
      setSaving(false);
    }
  };

  const afterRetired = () => {
    setRetireOpen(false);
    loadVessel().catch((e) => notify.error('Could not reload the vessel', e.message));
    setRefreshKey((k) => k + 1);
  };

  const ownerView = (code: unknown) =>
    code ? (
      <>
        {String(code)}
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
        {ownerUnavailable && <div style={{ opacity: 0.65, fontSize: 12 }}>Owner details temporarily unavailable</div>}
      </>
    ) : (
      '—'
    );

  const currentYear = new Date().getFullYear();

  const sections: ResourceFormSection<Vessel>[] = [
    {
      key: 'identity',
      title: 'Identity',
      fields: [
        {
          name: 'registryCode',
          label: 'Registry code',
          type: 'text',
          modeOverride: 'view', // immutable after registration — the API ignores it on PUT
          tooltip: 'Identity does not change; retire and re-register to reuse a code',
        },
        { name: 'name', label: 'Ship name', type: 'text', required: true, rules: [{ required: true, max: 255 }] },
        {
          name: 'typeCode',
          label: 'Vessel type',
          type: 'select',
          required: true,
          options: types.map((t) => ({ value: t.code, label: t.name })),
          viewRender: (code) => <AppTag>{typeName(String(code ?? ''))}</AppTag>,
        },
      ],
    },
    {
      key: 'details',
      title: 'Details',
      fields: [
        {
          name: 'buildYear',
          label: 'Build year',
          type: 'custom',
          tooltip: `1800 – ${currentYear}, not in the future`,
          render: ({ value, onChange }) => (
            <NumberField value={value} onChange={onChange} min={1800} max={currentYear} precision={0} />
          ),
          viewRender: (value) => (value ? String(value) : '—'),
        },
        { name: 'homePort', label: 'Home port', type: 'text', rules: [{ max: 100 }] },
        {
          name: 'grossTonnage',
          label: 'Gross tonnage',
          type: 'custom',
          tooltip: 'Must be positive',
          render: ({ value, onChange }) => <NumberField value={value} onChange={onChange} min={0.01} />,
          viewRender: (value) => (value ? String(value) : '—'),
        },
        {
          name: 'ownerIsikukood',
          label: 'Owner personal code',
          type: 'text',
          // Read-only once an owner exists: changes go through "Transfer ownership".
          modeOverride: vessel.ownerIsikukood ? 'view' : undefined,
          tooltip: vessel.ownerIsikukood ? 'Use "Transfer ownership" below to change the owner' : undefined,
          rules: [{ pattern: /^\d{11}$/, message: 'Exactly 11 digits' }],
          placeholder: '38102130265',
          viewRender: ownerView,
        },
      ],
    },
  ];

  return (
    <div>
      {retired && (
        <div style={{ padding: '16px 24px 0' }}>
          <Alert
            type="warning"
            showIcon
            message={`Retired on ${vessel.retiredOn ?? '—'} · ${reasonName(vessel.retirementReason)}`}
            description={
              <>
                {vessel.retirementNote && <div>{vessel.retirementNote}</div>}
                <div>The records stay readable; editing, licenses and transfers are closed.</div>
              </>
            }
          />
        </div>
      )}

      <ResourceForm<Vessel>
        mode={mode}
        entityLabel="Vessel"
        icon={<ContainerOutlined />}
        name={vessel.name}
        subtitle={vessel.registryCode}
        chips={
          vessel.typeCode ? (
            <>
              <AppTag>{typeName(vessel.typeCode)}</AppTag> <VesselStatusTag retiredOn={vessel.retiredOn} />
            </>
          ) : undefined
        }
        loading={loading}
        saving={saving}
        deleting={false}
        form={form}
        dataController={dc}
        initialValues={vessel}
        sections={sections}
        onBack={() => navigate('/vessels')}
        backTooltip="Back to the list"
        // A retired vessel is frozen: no Edit button, no Retire in the "…" menu.
        onEdit={retired ? undefined : () => setMode('edit')}
        onCancel={() => {
          dc.reset();
          form.setFieldsValue(dc.original ?? dc.current);
          setMode('view');
          if (searchParams.has('edit')) setSearchParams({}, { replace: true });
        }}
        onSave={save}
        onDelete={retired ? undefined : () => setRetireOpen(true)}
        deleteLabel="Retire"
        deleteConfirmText="Retire this vessel? Next you choose the retirement date and reason."
        sidebar={
          <div style={{ display: 'grid', gap: 16 }}>
          {!retired && (
            <AppCard title="Actions" size="small">
              <Button danger block icon={<StopOutlined />} onClick={() => setRetireOpen(true)}>
                Retire vessel
              </Button>
            </AppCard>
          )}
          <AppCard title="Metadata" size="small">
            <FieldItem label="Created at">
              <AppTimestamp value={vessel.sysCreatedAt} fallback="—" />
            </FieldItem>
            <FieldItem label="Created by">{vessel.sysCreatedBy ?? '—'}</FieldItem>
            <FieldItem label="Modified at">
              <AppTimestamp value={vessel.sysModifiedAt} fallback="—" />
            </FieldItem>
            <FieldItem label="Modified by">{vessel.sysModifiedBy ?? '—'}</FieldItem>
            <FieldItem label="Version">{vessel.sysVersion ?? '—'}</FieldItem>
          </AppCard>
          </div>
        }
      />

      {!loading && vessel.id && (
        <div style={{ display: 'grid', gap: 16, padding: 24 }}>
          <LicensesCard vesselId={vesselId} retired={retired} refreshKey={refreshKey} />
          <OwnershipCard
            vesselId={vesselId}
            retired={retired}
            refreshKey={refreshKey}
            onTransferred={() => loadVessel().catch(() => undefined)}
          />
        </div>
      )}

      <RetireModal
        vesselId={vesselId}
        vesselLabel={`${vessel.name ?? ''} · ${vessel.registryCode ?? ''}`}
        open={retireOpen}
        onClose={() => setRetireOpen(false)}
        onRetired={afterRetired}
      />
    </div>
  );
};
