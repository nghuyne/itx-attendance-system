import api from './api';

export const reportService = {
  exportAttendance: async (from: string, to: string, employeeId?: string): Promise<void> => {
    const response = await api.get('/admin/attendance/export', {
      params: { from, to, ...(employeeId ? { employeeId } : {}) },
      responseType: 'blob',
    });
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
