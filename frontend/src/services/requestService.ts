import api from './api';
import type {
  ExceptionRequestCreateDto,
  ExceptionRequestDto,
  AdjustmentRequestCreateDto,
  AdjustmentRequestDto,
} from '../types/api';

export const requestService = {
  submitExceptionRequest: (data: ExceptionRequestCreateDto): Promise<ExceptionRequestDto> =>
    api.post<ExceptionRequestDto>('/requests/exception', data).then(r => r.data),

  submitAdjustmentRequest: (data: AdjustmentRequestCreateDto): Promise<AdjustmentRequestDto> =>
    api.post<AdjustmentRequestDto>('/requests/adjustment', data).then(r => r.data),
};
