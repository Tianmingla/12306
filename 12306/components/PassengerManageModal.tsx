import React, { useEffect, useState } from 'react';
import { X, Trash2, UserPlus } from 'lucide-react';
import type { PassengerApi, PassengerSavePayload } from '../types';
import { listPassengers, addPassenger, deletePassenger } from '../services/passengerService';

interface PassengerManageModalProps {
  isOpen: boolean;
  onClose: () => void;
}

function passengerTypeLabel(t: number): string {
  switch (t) {
    case 1: return '成人';
    case 2: return '儿童';
    case 3: return '学生';
    default: return '其他';
  }
}

const PassengerManageModal: React.FC<PassengerManageModalProps> = ({ isOpen, onClose }) => {
  const [items, setItems] = useState<PassengerApi[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [form, setForm] = useState<PassengerSavePayload>({
    realName: '',
    idCardType: 1,
    idCardNumber: '',
    passengerType: 1,
    phone: '',
  });
  const [submitting, setSubmitting] = useState(false);

  const refresh = async () => {
    setLoading(true);
    setError('');
    try {
      const data = await listPassengers();
      setItems(data);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : '加载失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (!isOpen) return;
    void refresh();
  }, [isOpen]);

  const handleAdd = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setError('');
    try {
      await addPassenger({
        ...form,
        phone: form.phone?.trim() || undefined,
      });
      setForm({
        realName: '',
        idCardType: 1,
        idCardNumber: '',
        passengerType: 1,
        phone: '',
      });
      await refresh();
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : '添加失败');
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = async (id: number) => {
    if (!confirm('确定删除该乘车人？')) return;
    setError('');
    try {
      await deletePassenger(id);
      await refresh();
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : '删除失败');
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center p-4 bg-black/50 backdrop-blur-sm">
      <div className="bg-white rounded-2xl w-full max-w-lg shadow-2xl max-h-[90vh] flex flex-col">
        <div className="flex items-center justify-between p-4 border-b border-gray-100">
          <h2 className="text-lg font-bold text-gray-800">乘车人管理</h2>
          <button type="button" onClick={onClose} className="p-1 rounded-full hover:bg-gray-100 text-gray-500">
            <X className="h-5 w-5" />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto p-4 space-y-4">
          {error && <div className="text-red-500 text-sm">{error}</div>}

          <div className="space-y-2">
            <h3 className="text-sm font-semibold text-gray-700 flex items-center gap-2">
              <UserPlus className="h-4 w-4" /> 添加乘车人
            </h3>
            <form onSubmit={handleAdd} className="grid grid-cols-1 gap-2 text-sm">
              <input
                required
                placeholder="姓名"
                value={form.realName}
                onChange={(e) => setForm((f) => ({ ...f, realName: e.target.value }))}
                className="w-full px-3 py-2 border border-gray-200 rounded-lg"
              />
              <input
                required
                placeholder="证件号码"
                value={form.idCardNumber}
                onChange={(e) => setForm((f) => ({ ...f, idCardNumber: e.target.value }))}
                className="w-full px-3 py-2 border border-gray-200 rounded-lg"
              />
              <div className="flex gap-2">
                <select
                  value={form.idCardType}
                  onChange={(e) => setForm((f) => ({ ...f, idCardType: Number(e.target.value) }))}
                  className="flex-1 px-3 py-2 border border-gray-200 rounded-lg"
                >
                  <option value={1}>身份证</option>
                  <option value={2}>护照</option>
                </select>
                <select
                  value={form.passengerType}
                  onChange={(e) => setForm((f) => ({ ...f, passengerType: Number(e.target.value) }))}
                  className="flex-1 px-3 py-2 border border-gray-200 rounded-lg"
                >
                  <option value={1}>成人</option>
                  <option value={2}>儿童</option>
                  <option value={3}>学生</option>
                </select>
              </div>
              <input
                placeholder="联系手机（可选）"
                value={form.phone || ''}
                onChange={(e) => setForm((f) => ({ ...f, phone: e.target.value }))}
                className="w-full px-3 py-2 border border-gray-200 rounded-lg"
              />
              <button
                type="submit"
                disabled={submitting}
                className="w-full py-2 bg-blue-600 text-white rounded-lg font-medium hover:bg-blue-700 disabled:opacity-50"
              >
                {submitting ? '提交中…' : '保存乘车人'}
              </button>
            </form>
          </div>

          <div>
            <h3 className="text-sm font-semibold text-gray-700 mb-2">已添加乘车人</h3>
            {loading ? (
              <p className="text-sm text-gray-500">加载中…</p>
            ) : items.length === 0 ? (
              <p className="text-sm text-gray-500">暂无乘车人，请先添加。</p>
            ) : (
              <ul className="divide-y divide-gray-100 border border-gray-100 rounded-lg">
                {items.map((p) => (
                  <li key={p.id} className="flex items-center justify-between gap-2 px-3 py-2 text-sm">
                    <div>
                      <span className="font-medium text-gray-800">{p.realName}</span>
                      <span className="text-gray-500 ml-2">{passengerTypeLabel(p.passengerType)}</span>
                      <div className="text-xs text-gray-400 mt-0.5">{p.idCardNumber}</div>
                    </div>
                    <button
                      type="button"
                      onClick={() => void handleDelete(p.id)}
                      className="p-2 text-red-500 hover:bg-red-50 rounded-lg"
                      aria-label="删除"
                    >
                      <Trash2 className="h-4 w-4" />
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

export default PassengerManageModal;
