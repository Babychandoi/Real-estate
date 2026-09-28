import { useState } from 'react';
import { Download, FileUp } from 'lucide-react';
import { Button } from '@/shared/ui/Button';
import { Dialog } from '@/shared/ui/Dialog';
import { InlineFeedback } from '@/shared/ui/InlineFeedback';
import { downloadTemplate, importCsv, type ImportReport } from './api';

interface ImportDialogProps {
  open: boolean;
  onClose: () => void;
  onImported: () => void;
}

/** CSV import: check the file first (nothing is saved), then create every row as a draft in one step. */
export function ImportDialog({ open, onClose, onImported }: ImportDialogProps) {
  const [file, setFile] = useState<File | null>(null);
  const [report, setReport] = useState<ImportReport | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const run = async (dryRun: boolean) => {
    if (!file) return;
    setBusy(true);
    setError(null);
    try {
      const result = await importCsv(file, dryRun);
      setReport(result);
      if (!dryRun && result.committed) onImported();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không nhập được tệp.');
    } finally {
      setBusy(false);
    }
  };

  const close = () => {
    setFile(null);
    setReport(null);
    setError(null);
    onClose();
  };

  const clean =
    report != null && report.fileErrors.length === 0 && report.validRows === report.totalRows && report.totalRows > 0;

  return (
    <Dialog
      open={open}
      onClose={close}
      size="lg"
      title="Nhập tin từ tệp CSV"
      description="Mỗi dòng thành một tin nháp. Kiểm tra trước, sửa các dòng lỗi rồi nhập; tin nháp cần được gửi duyệt riêng."
      footer={
        report?.committed ? (
          <Button onClick={close}>Xong</Button>
        ) : (
          <>
            <Button variant="outline" onClick={close}>
              Hủy
            </Button>
            {clean ? (
              <Button isLoading={busy} onClick={() => void run(false)}>
                Tạo {report.totalRows} tin nháp
              </Button>
            ) : (
              <Button isLoading={busy} disabled={!file} onClick={() => void run(true)}>
                Kiểm tra tệp
              </Button>
            )}
          </>
        )
      }
    >
      <div className="flex flex-col gap-4">
        <Button
          variant="ghost"
          size="sm"
          leftIcon={<Download className="h-4 w-4" />}
          onClick={() => void downloadTemplate().catch(() => setError('Không tải được tệp mẫu.'))}
          className="self-start"
        >
          Tải tệp mẫu (UTF-8)
        </Button>
        <label className="flex min-h-11 cursor-pointer items-center gap-2 rounded-lg border border-dashed border-outline p-3 text-body-sm focus-within:ring-2 focus-within:ring-primary">
          <FileUp className="h-5 w-5" aria-hidden="true" />
          <span>{file ? file.name : 'Chọn tệp .csv (tối đa 200 dòng, 1 MB)'}</span>
          <input
            type="file"
            accept=".csv,text/csv"
            className="sr-only"
            data-testid="import-file"
            onChange={(event) => {
              setFile(event.target.files?.[0] ?? null);
              setReport(null);
            }}
          />
        </label>
        {error && <InlineFeedback kind="error" title={error} />}
        {report && (
          <>
            {report.committed ? (
              <InlineFeedback
                kind="success"
                title={report.duplicate ? 'Tệp này đã được nhập trước đó' : `Đã tạo ${report.totalRows} tin nháp`}
              >
                Tin nhập từ tệp nằm ở mục Nháp. Mở từng tin để thêm ảnh, vị trí rồi gửi duyệt.
              </InlineFeedback>
            ) : (
              <InlineFeedback
                kind={clean ? 'success' : 'warning'}
                title={`${report.validRows}/${report.totalRows} dòng hợp lệ`}
              >
                {clean
                  ? 'Chưa có gì được lưu. Bấm “Tạo tin nháp” để nhập.'
                  : 'Sửa các dòng lỗi trong tệp rồi kiểm tra lại.'}
              </InlineFeedback>
            )}
            {report.fileErrors.length > 0 && (
              <ul className="list-disc pl-5 text-body-sm text-error">
                {report.fileErrors.map((issue, index) => (
                  <li key={index}>{issue.message}</li>
                ))}
              </ul>
            )}
            {report.rows.some((row) => row.errors.length || row.warnings.length) && (
              <div className="max-h-72 overflow-auto rounded-lg border border-outline-variant">
                <table className="w-full text-left text-body-sm">
                  <caption className="sr-only">Kết quả kiểm tra từng dòng</caption>
                  <thead className="bg-surface-container">
                    <tr>
                      <th scope="col" className="p-2">
                        Dòng
                      </th>
                      <th scope="col" className="p-2">
                        Tiêu đề
                      </th>
                      <th scope="col" className="p-2">
                        Cần sửa
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    {report.rows
                      .filter((row) => row.errors.length || row.warnings.length)
                      .map((row) => (
                        <tr key={row.line} className="border-t border-outline-variant align-top">
                          <td className="p-2">{row.line}</td>
                          <td className="p-2">{row.title || '—'}</td>
                          <td className="p-2">
                            <ul>
                              {row.errors.map((issue, index) => (
                                <li key={index} className="text-error">
                                  {issue.field ? `${issue.field}: ` : ''}
                                  {issue.message}
                                </li>
                              ))}
                              {row.warnings.map((warning, index) => (
                                <li key={`w${index}`} className="text-on-surface-variant">
                                  {warning}
                                </li>
                              ))}
                            </ul>
                          </td>
                        </tr>
                      ))}
                  </tbody>
                </table>
              </div>
            )}
          </>
        )}
      </div>
    </Dialog>
  );
}
