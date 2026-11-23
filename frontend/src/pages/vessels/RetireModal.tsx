import { useEffect, useState } from 'react';
import { Alert, DatePicker, Form, Input, Modal, Select } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { useAppNotification } from '@helex/ui';
import { isCurrentlyValid, vesselsApi, type CodeName } from '../../vesselsApi';

interface Props {
  vesselId: number;
  vesselLabel: string;
  open: boolean;
  onClose: () => void;
  onRetired: () => void;
}

/**
 * Retire Vessel modal (VESSELS.01 § UI › retire modal): date (not in the future),
 * reason from the reference list, note required for OTHER, and a warning with the
 * number of currently valid licenses the server will revoke.
 */
export const RetireModal = ({ vesselId, vesselLabel, open, onClose, onRetired }: Props) => {
  const notify = useAppNotification();
  const [form] = Form.useForm<{ retiredOn: Dayjs; reason: string; note?: string }>();
  const [reasons, setReasons] = useState<CodeName[]>([]);
  const [validCount, setValidCount] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);
  const reason = Form.useWatch('reason', form);

  useEffect(() => {
    if (!open) return;
    form.resetFields();
    form.setFieldsValue({ retiredOn: dayjs() });
    setValidCount(null);
    vesselsApi.retirementReasons().then(setReasons).catch(() => setReasons([]));
    vesselsApi
      .licenses(vesselId)
      .then((list) => setValidCount(list.filter((l) => isCurrentlyValid(l.effectiveStatus)).length))
      .catch(() => setValidCount(null));
  }, [open, vesselId, form]);

  const submit = async () => {
    const v = await form.validateFields().catch(() => null); // invalid: the form shows why
    if (!v) return;
    setBusy(true);
    try {
      await vesselsApi.retire(vesselId, {
        retiredOn: v.retiredOn.format('YYYY-MM-DD'),
        reason: v.reason,
        note: v.note?.trim() || undefined,
      });
      notify.success('Vessel retired', vesselLabel);
      onRetired();
    } catch (e) {
      notify.error('Retirement refused', (e as Error).message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal
      title={`Retire ${vesselLabel}`}
      open={open}
      forceRender // keeps the form mounted, so Form.useWatch always has a connected form
      okText="Retire vessel"
      okButtonProps={{ danger: true }}
      confirmLoading={busy}
      onOk={submit}
      onCancel={onClose}
    >
      <Alert
        type="warning"
        showIcon
        style={{ marginBottom: 16 }}
        message={
          validCount === null
            ? 'Every license that is still valid will be revoked with the reason "Vessel retired".'
            : `${validCount} currently valid license(s) will be revoked with the reason "Vessel retired".`
        }
        description="The vessel leaves the active register, its records stay readable, and its registry code becomes free for reuse. This cannot be undone."
      />
      <Form form={form} layout="vertical" requiredMark>
        <Form.Item name="retiredOn" label="Retirement date" rules={[{ required: true }]}>
          <DatePicker style={{ width: '100%' }} maxDate={dayjs()} />
        </Form.Item>
        <Form.Item name="reason" label="Reason" rules={[{ required: true, message: 'Choose a reason' }]}>
          <Select options={reasons.map((r) => ({ value: r.code, label: r.name }))} placeholder="Choose a reason" />
        </Form.Item>
        <Form.Item
          name="note"
          label="Note"
          dependencies={['reason']}
          rules={[
            { max: 500 },
            {
              required: reason === 'OTHER',
              whitespace: true,
              message: 'A note is required when the reason is Other',
            },
          ]}
        >
          <Input.TextArea rows={3} placeholder={reason === 'OTHER' ? 'Required for Other' : 'Optional'} />
        </Form.Item>
      </Form>
    </Modal>
  );
};
