import { Tag } from 'antd';
import type { LicenseEffectiveStatus } from '../../vesselsApi';

/** Label and colour per derived license status (VESSELS.01 § Derived Effective Status). */
const STATUS: Record<LicenseEffectiveStatus, { label: string; color: string }> = {
  VALID: { label: 'Valid', color: 'green' },
  EXPIRING_SOON: { label: 'Expiring soon', color: 'orange' },
  NOT_YET_VALID: { label: 'Not yet valid', color: 'blue' },
  EXPIRED: { label: 'Expired', color: 'default' },
  REVOKED: { label: 'Revoked', color: 'red' },
};

export const LicenseStatusTag = ({ status }: { status: LicenseEffectiveStatus }) => {
  const s = STATUS[status] ?? { label: status, color: 'default' };
  return <Tag color={s.color}>{s.label}</Tag>;
};

/** Active / Retired tag for a vessel. */
export const VesselStatusTag = ({ retiredOn }: { retiredOn?: string | null }) =>
  retiredOn ? <Tag color="red">Retired {retiredOn}</Tag> : <Tag color="green">Active</Tag>;
