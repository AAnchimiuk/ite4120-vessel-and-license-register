import { useCallback, useEffect, useState } from 'react';
import { Alert, DatePicker, Form, Input, Modal, Popconfirm, Select, Table, Tag } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import dayjs, { type Dayjs } from 'dayjs';
import { AppButtonPrimary, AppButtonSecondary, AppCard, useAppNotification } from '@helex/ui';
import { isCurrentlyValid, vesselsApi, type CodeName, type License } from '../../vesselsApi';
import { LicenseStatusTag } from './LicenseStatusTag';

const fmt = (d: Dayjs) => d.format('YYYY-MM-DD');

interface Props {
  vesselId: number;
  retired: boolean;
  /** Bumped by the page when something outside this card changed the licenses (e.g. retirement). */
  refreshKey: number;
}

/**
 * Licenses of one vessel (VESSELS.01 § UI › licenses sub-list, compliance summary,
 * add / revoke / delete actions). All licenses are listed — history included —
 * each with its server-derived effective status.
 */
export const LicensesCard = ({ vesselId, retired, refreshKey }: Props) => {
  const notify = useAppNotification();
  const [licenses, setLicenses] = useState<License[]>([]);
  const [types, setTypes] = useState<CodeName[]>([]);
  const [loading, setLoading] = useState(true);
  const [adding, setAdding] = useState(false);
  const [revoking, setRevoking] = useState<License | null>(null);
  const [busy, setBusy] = useState(false);
  const [addForm] = Form.useForm<{ licenseNumber: string; licenseType: string; issuedOn: Dayjs; validUntil: Dayjs }>();
  const [revokeForm] = Form.useForm<{ revokedOn: Dayjs; reason: string }>();

  const load = useCallback(() => {
    setLoading(true);
    vesselsApi
      .licenses(vesselId)
      .then(setLicenses)
      .catch((e) => notify.error('Could not load licenses', e.message))
      .finally(() => setLoading(false));
  }, [vesselId, notify]);

  useEffect(load, [load, refreshKey]);

  useEffect(() => {
    vesselsApi.licenseTypes().then(setTypes).catch(() => setTypes([]));
  }, []);

  const typeName = (code: string) => types.find((t) => t.code === code)?.name ?? code;

  // Compliance: per license type ever issued, is at least one currently valid?
  const typesIssued = Array.from(new Set(licenses.map((l) => l.licenseType)));
  const compliance = typesIssued.map((type) => ({
    type,
    ok: licenses.some((l) => l.licenseType === type && isCurrentlyValid(l.effectiveStatus)),
  }));

  const openAdd = () => {
    addForm.resetFields();
    addForm.setFieldsValue({ issuedOn: dayjs() });
    setAdding(true);
  };

  const submitAdd = async () => {
    const v = await addForm.validateFields().catch(() => null); // invalid: the form shows why
    if (!v) return;
    setBusy(true);
    try {
      const created = await vesselsApi.addLicense(vesselId, {
        licenseNumber: v.licenseNumber.trim(),
        licenseType: v.licenseType,
        issuedOn: fmt(v.issuedOn),
        validUntil: fmt(v.validUntil),
      });
      notify.success('License recorded', created.licenseNumber);
      setAdding(false);
      load();
    } catch (e) {
      notify.error('Could not record the license', (e as Error).message);
    } finally {
      setBusy(false);
    }
  };

  const openRevoke = (license: License) => {
    revokeForm.resetFields();
    revokeForm.setFieldsValue({ revokedOn: dayjs() });
    setRevoking(license);
  };

  const submitRevoke = async () => {
    if (!revoking) return;
    const v = await revokeForm.validateFields().catch(() => null);
    if (!v) return;
    setBusy(true);
    try {
      await vesselsApi.revokeLicense(vesselId, revoking.id, { revokedOn: fmt(v.revokedOn), reason: v.reason.trim() });
      notify.success('License revoked', revoking.licenseNumber);
      setRevoking(null);
      load();
    } catch (e) {
      notify.error('Could not revoke the license', (e as Error).message);
    } finally {
      setBusy(false);
    }
  };

  const remove = async (license: License) => {
    try {
      await vesselsApi.deleteLicense(vesselId, license.id);
      notify.success('Scheduled license deleted', license.licenseNumber);
      load();
    } catch (e) {
      notify.error('Could not delete the license', (e as Error).message);
    }
  };

  return (
    <AppCard title="Licenses">
      {compliance.length > 0 && (
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginBottom: 12 }}>
          {compliance.map((c) => (
            <Tag key={c.type} color={c.ok ? 'green' : 'red'}>
              {c.ok ? '✓' : '✗'} {typeName(c.type)}
            </Tag>
          ))}
        </div>
      )}

      {!retired && (
        <div style={{ marginBottom: 12 }}>
          <AppButtonPrimary icon={<PlusOutlined />} onClick={openAdd}>
            Add license
          </AppButtonPrimary>
        </div>
      )}

      <Table<License>
        rowKey="id"
        size="small"
        loading={loading}
        dataSource={licenses}
        pagination={false}
        locale={{ emptyText: 'No licenses yet' }}
        columns={[
          { key: 'licenseNumber', title: 'Number', dataIndex: 'licenseNumber' },
          { key: 'licenseType', title: 'Type', dataIndex: 'licenseType', render: (code: string) => typeName(code) },
          { key: 'issuedOn', title: 'Issued', dataIndex: 'issuedOn' },
          { key: 'validUntil', title: 'Valid until', dataIndex: 'validUntil' },
          {
            key: 'effectiveStatus',
            title: 'Status',
            dataIndex: 'effectiveStatus',
            render: (_: unknown, l: License) => (
              <>
                <LicenseStatusTag status={l.effectiveStatus} />
                {l.revocationReason && (
                  <div style={{ opacity: 0.65, fontSize: 12 }}>
                    {l.revokedOn}: {l.revocationReason}
                  </div>
                )}
              </>
            ),
          },
          {
            key: 'actions',
            title: '',
            render: (_: unknown, l: License) => {
              if (retired) return null;
              if (isCurrentlyValid(l.effectiveStatus)) {
                return <AppButtonSecondary onClick={() => openRevoke(l)}>Revoke</AppButtonSecondary>;
              }
              if (l.effectiveStatus === 'NOT_YET_VALID') {
                return (
                  <Popconfirm
                    title="Delete this scheduled license?"
                    description="It has not taken effect yet."
                    okText="Delete"
                    okButtonProps={{ danger: true }}
                    onConfirm={() => remove(l)}
                  >
                    <AppButtonSecondary>Delete</AppButtonSecondary>
                  </Popconfirm>
                );
              }
              return null;
            },
          },
        ]}
      />

      <Modal
        title="Add license"
        open={adding}
        forceRender
        okText="Add"
        confirmLoading={busy}
        onOk={submitAdd}
        onCancel={() => setAdding(false)}
      >
        <Form form={addForm} layout="vertical" requiredMark>
          <Form.Item name="licenseNumber" label="License number" rules={[{ required: true, whitespace: true, max: 50 }]}>
            <Input placeholder="LIC-N-500" />
          </Form.Item>
          <Form.Item name="licenseType" label="License type" rules={[{ required: true }]}>
            <Select options={types.map((t) => ({ value: t.code, label: t.name }))} placeholder="Choose a type" />
          </Form.Item>
          <Form.Item name="issuedOn" label="Issue date" rules={[{ required: true }]}>
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item
            name="validUntil"
            label="Valid until"
            dependencies={['issuedOn']}
            rules={[
              { required: true },
              ({ getFieldValue }) => ({
                validator: (_, value?: Dayjs) => {
                  const issued: Dayjs | undefined = getFieldValue('issuedOn');
                  return !value || !issued || !value.isBefore(issued, 'day')
                    ? Promise.resolve()
                    : Promise.reject(new Error('Must not be before the issue date'));
                },
              }),
            ]}
          >
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={`Revoke license ${revoking?.licenseNumber ?? ''}`}
        open={revoking !== null}
        forceRender
        okText="Revoke"
        okButtonProps={{ danger: true }}
        confirmLoading={busy}
        onOk={submitRevoke}
        onCancel={() => setRevoking(null)}
      >
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 16 }}
          message="A revoked license is never valid again. It stays in the history."
        />
        <Form form={revokeForm} layout="vertical" requiredMark>
          <Form.Item name="revokedOn" label="Revocation date" rules={[{ required: true }]}>
            <DatePicker style={{ width: '100%' }} maxDate={dayjs()} />
          </Form.Item>
          <Form.Item
            name="reason"
            label="Reason"
            rules={[{ required: true, whitespace: true, message: 'A reason is required' }, { max: 500 }]}
          >
            <Input.TextArea rows={3} placeholder="Failed safety inspection" />
          </Form.Item>
        </Form>
      </Modal>
    </AppCard>
  );
};
