import api from './api';
import type {
  ExceptionRequestCreateDto,
  ExceptionRequestDto,
  AdjustmentRequestCreateDto,
  AdjustmentRequestDto,
  RequestSummaryDto,
} from '../types/api';

export const requestService = {
  submitExceptionRequest: (data: ExceptionRequestCreateDto): Promise<ExceptionRequestDto> =>
    api.post<ExceptionRequestDto>('/requests/exception', data).then(r => r.data),

  submitAdjustmentRequest: (data: AdjustmentRequestCreateDto): Promise<AdjustmentRequestDto> =>
    api.post<AdjustmentRequestDto>('/requests/adjustment', data).then(r => r.data),

  getPending: (): Promise<RequestSummaryDto[]> =>
    api.get<RequestSummaryDto[]>('/requests/pending').then(r => r.data),

  getByStatus: (status: 'APPROVED' | 'REJECTED'): Promise<RequestSummaryDto[]> =>
    api.get<RequestSummaryDto[]>('/requests', { params: { status } }).then(r => r.data),

  approve: (id: string): Promise<RequestSummaryDto> =>
    api.put<RequestSummaryDto>(`/requests/${id}/approve`).then(r => r.data),

  reject: (id: string, reason: string): Promise<RequestSummaryDto> =>
    api.put<RequestSummaryDto>(`/requests/${id}/reject`, { reason }).then(r => r.data),
};
