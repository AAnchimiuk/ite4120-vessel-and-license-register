import { useCallback, useEffect, useState } from 'react';
import { Alert, DatePicker, Form, Input, Modal, Table, Tag } from 'antd';
import { SwapOutlined } from '@ant-design/icons';
import dayjs, { type Dayjs } from 'dayjs';
import { AppButtonPrimary, AppCard, useAppNotification } from '@helex/ui';
import { vesselsApi, type OwnerInfo, type OwnershipPeriod } from '../../vesselsApi';

interface Props {
  vesselId: number;
  retired: boolean;
  refreshKey: number;
  /** Called after a successful transfer, so the page can reload the vessel's current owner. */
  onTransferred: () => void;
}

/** Owner name per period: fetched live, never stored; 'unavailable' when the registry is down. */
type OwnerLookup = OwnerInfo | 'unavailable' | undefined;

/**
 * Ownership history and transfer (VESSELS.01 § UI › ownership history, transfer modal).
 * Names are resolved live through the registry adapter for every period. A transfer
 * fails closed: the server verifies the new code with the registry before saving.
 */
export const OwnershipCard = ({ vesselId, retired, refreshKey, onTransferred }: Props) => {
  const notify = useAppNotification();
  const [periods, setPeriods] = useState<OwnershipPeriod[]>([]);
  const [owners, setOwners] = useState<Record<number, OwnerLookup>>({});
  const [loading, setLoading] = useState(true);
  const [transferring, setTransferring] = useState(false);
  const [busy, setBusy] = useState(false);
  const [form] = Form.useForm<{ newOwnerIsikukood: string; transferDate: Dayjs }>();

  const load = useCallback(() => {
    setLoading(true);
    vesselsApi
      .ownership(vesselId)
      .then((list) => {
        setPeriods(list);
        setOwners({});
        list.forEach((p) =>
          vesselsApi
            .ownerOfPeriod(vesselId, p.id)
            .then((o) => setOwners((prev) => ({ ...prev, [p.id]: o })))
            .catch(() => setOwners((prev) => ({ ...prev, [p.id]: 'unavailable' }))),
        );
      })
      .catch((e) => notify.error('Could not load the ownership history', e.message))
      .finally(() => setLoading(false));
  }, [vesselId, notify]);

  useEffect(load, [load, refreshKey]);

  const openTransfer = () => {
    form.resetFields();
    form.setFieldsValue({ transferDate: dayjs() });
    setTransferring(true);
  };

  const submit = async () => {
    const v = await form.validateFields().catch(() => null); // invalid: the form shows why
    if (!v) return;
    setBusy(true);
    try {
      await vesselsApi.transfer(vesselId, {
        newOwnerIsikukood: v.newOwnerIsikukood.trim(),
        transferDate: v.transferDate.format('YYYY-MM-DD'),
      });
      notify.success('Ownership transferred', v.newOwnerIsikukood);
      setTransferring(false);
      load();
      onTransferred();
    } catch (e) {
      // 400 unknown person / same owner / bad date, 409 retired, 502 registry down — verbatim.
      notify.error('Transfer refused', (e as Error).message);
    } finally {
      setBusy(false);
    }
  };

  const ownerName = (id: number) => {
    const o = owners[id];
    if (o === undefined) return '…';
    if (o === 'unavailable') return <span style={{ opacity: 0.65 }}>Unavailable</span>;
    return `${o.firstName} ${o.lastName}`;
  };

  return (
    <AppCard title="Ownership history">
      {!retired && (
        <div style={{ marginBottom: 12 }}>
          <AppButtonPrimary icon={<SwapOutlined />} onClick={openTransfer}>
            Transfer ownership
          </AppButtonPrimary>
        </div>
      )}

      <Table<OwnershipPeriod>
        rowKey="id"
        size="small"
        loading={loading}
        dataSource={periods}
        pagination={false}
        locale={{ emptyText: 'No owner recorded' }}
        columns={[
          { key: 'name', title: 'Owner', render: (_: unknown, p: OwnershipPeriod) => ownerName(p.id) },
          { key: 'ownerIsikukood', title: 'Personal code', dataIndex: 'ownerIsikukood' },
          { key: 'ownedFrom', title: 'From', dataIndex: 'ownedFrom' },
          {
            key: 'ownedUntil',
            title: 'Until',
            dataIndex: 'ownedUntil',
            render: (until?: string | null) => until ?? <Tag color="green">current</Tag>,
          },
        ]}
      />

      <Modal
        title="Transfer ownership"
        open={transferring}
        forceRender
        okText="Transfer"
        confirmLoading={busy}
        onOk={submit}
        onCancel={() => setTransferring(false)}
      >
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
          message="The new owner's code is checked live with the population registry. If the registry cannot be reached, nothing is saved."
        />
        <Form form={form} layout="vertical" requiredMark>
          <Form.Item
            name="newOwnerIsikukood"
            label="New owner personal code"
            rules={[
              { required: true, message: 'Enter the personal code' },
              { pattern: /^\d{11}$/, message: 'Exactly 11 digits' },
            ]}
            extra="Known to the mock registry: 38102130265, 47503121234, 50011020017"
          >
            <Input placeholder="47503121234" maxLength={11} />
          </Form.Item>
          <Form.Item name="transferDate" label="Transfer date" rules={[{ required: true }]}>
            <DatePicker style={{ width: '100%' }} maxDate={dayjs()} />
          </Form.Item>
        </Form>
      </Modal>
    </AppCard>
  );
};
