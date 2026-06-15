import api from './api';

export const reportService = {
  exportAttendance: async (startDate: string, endDate: string): Promise<void> => {
    try {
      const response = await api.get('/reports/attendance/export', {
        params: { startDate, endDate },
        responseType: 'blob',
      });
      
      // Create a URL for the blob
      const url = window.URL.createObjectURL(new Blob([response.data]));
      const link = document.createElement('a');
      link.href = url;
      
      // Extract filename from header or use default
      let filename = `attendance_report_${startDate}_to_${endDate}.xlsx`;
      const contentDisposition = response.headers['content-disposition'];
      if (contentDisposition && contentDisposition.includes('filename=')) {
        filename = contentDisposition.split('filename=')[1].replace(/"/g, '');
      }
      
      link.setAttribute('download', filename);
      document.body.appendChild(link);
      link.click();
      
      // Cleanup
      link.parentNode?.removeChild(link);
      window.URL.revokeObjectURL(url);
    } catch (error) {
      console.error('Export failed:', error);
      throw error;
    }
  },
};
