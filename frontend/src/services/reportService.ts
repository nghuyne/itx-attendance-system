import api from './api';

export const reportService = {
  exportAttendance: async (from: string, to: string, employeeId?: string): Promise<void> => {
    let response;
    try {
      response = await api.get('/admin/attendance/export', {
        params: { from, to, ...(employeeId ? { employeeId } : {}) },
        responseType: 'blob',
      });
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: unknown } };
      const blob = axiosErr.response?.data;
      if (blob instanceof Blob) {
        const text = await blob.text();
        try {
          const json = JSON.parse(text) as { message?: string; error?: string };
          throw new Error(json.message ?? json.error ?? 'Có lỗi xảy ra khi xuất báo cáo!');
        } catch (parseErr) {
          if (parseErr instanceof Error && parseErr.message !== text) throw parseErr;
          throw new Error(text || 'Có lỗi xảy ra khi xuất báo cáo!');
        }
      }
      throw err;
    }

    const url = window.URL.createObjectURL(new Blob([response.data]));
    const link = document.createElement('a');
    link.href = url;
    let filename = `attendance-${from}-${to}.xlsx`;
    const contentDisposition = response.headers['content-disposition'];
    if (contentDisposition?.includes('filename=')) {
      filename = contentDisposition.split('filename=')[1].replace(/"/g, '');
    }
    link.setAttribute('download', filename);
    document.body.appendChild(link);
    link.click();
    link.parentNode?.removeChild(link);
    window.URL.revokeObjectURL(url);
  },
};
