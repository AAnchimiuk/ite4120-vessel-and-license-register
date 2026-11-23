import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Form, Input, InputNumber, Select } from 'antd';
import { AppButtonPrimary, AppButtonSecondary, AppCard, useAppNotification } from '@helex/ui';
import { vesselsApi, type CodeName } from '../../vesselsApi';

interface FormValues {
  registryCode: string;
  name: string;
  typeCode: string;
  buildYear?: number | null;
  homePort?: string;
  grossTonnage?: number | null;
  ownerIsikukood?: string;
}

/**
 * Registration form (VESSELS.01 § UI › Register form). Field rules mirror the
 * spec; the server stays the authority — its 400 / 409 problem detail is shown verbatim.
 */
export const VesselCreate = () => {
  const navigate = useNavigate();
  const notify = useAppNotification();
  const [types, setTypes] = useState<CodeName[]>([]);
  const [saving, setSaving] = useState(false);
  const currentYear = new Date().getFullYear();

  useEffect(() => {
    vesselsApi.types().then(setTypes).catch((e) => notify.error('Could not load vessel types', e.message));
  }, [notify]);

  const submit = (values: FormValues) => {
    setSaving(true);
    vesselsApi
      .create({
        registryCode: values.registryCode.trim(),
        name: values.name.trim(),
        typeCode: values.typeCode,
        buildYear: values.buildYear ?? undefined,
        homePort: values.homePort?.trim() || undefined,
        grossTonnage: values.grossTonnage ?? undefined,
        ownerIsikukood: values.ownerIsikukood?.trim() || undefined,
      })
      .then((created) => {
        notify.success('Registered', `${created.name} · ${created.registryCode}`);
        navigate(`/vessels/${created.id}`);
      })
      .catch((e) => notify.error('Registration failed', e.message)) // 400 / 409 problem detail, verbatim
      .finally(() => setSaving(false));
  };

  return (
    <AppCard title="Register a new vessel" style={{ maxWidth: 640, margin: '24px auto' }}>
      <Form<FormValues> layout="vertical" onFinish={submit} requiredMark>
        <Form.Item
          name="registryCode"
          label="Registry code"
          rules={[{ required: true, whitespace: true, max: 50 }]}
          extra="Unique among active vessels — e.g. EST-5005"
        >
          <Input placeholder="EST-5005" />
        </Form.Item>
        <Form.Item name="name" label="Ship name" rules={[{ required: true, whitespace: true, max: 255 }]}>
          <Input placeholder="Tuulelind" />
        </Form.Item>
        <Form.Item name="typeCode" label="Vessel type" rules={[{ required: true }]}>
          <Select options={types.map((t) => ({ value: t.code, label: t.name }))} placeholder="Choose a type" />
        </Form.Item>
        <Form.Item name="buildYear" label="Build year" extra={`1800 – ${currentYear}, not in the future`}>
          <InputNumber style={{ width: '100%' }} min={1800} max={currentYear} precision={0} placeholder="2018" />
        </Form.Item>
        <Form.Item name="homePort" label="Home port" rules={[{ max: 100 }]}>
          <Input placeholder="Tallinn" />
        </Form.Item>
        <Form.Item name="grossTonnage" label="Gross tonnage" extra="Must be positive">
          <InputNumber style={{ width: '100%' }} min={0.01} step={0.5} placeholder="145.5" />
        </Form.Item>
        <Form.Item
          name="ownerIsikukood"
          label="Owner personal code"
          rules={[{ pattern: /^\d{11}$/, message: 'Exactly 11 digits' }]}
          extra="Try 38102130265 — the mock registry knows this person"
        >
          <Input placeholder="38102130265" maxLength={11} />
        </Form.Item>
        <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
          <AppButtonSecondary onClick={() => navigate('/vessels')}>Cancel</AppButtonSecondary>
          <AppButtonPrimary htmlType="submit" loading={saving}>
            Register
          </AppButtonPrimary>
        </div>
      </Form>
    </AppCard>
  );
};
