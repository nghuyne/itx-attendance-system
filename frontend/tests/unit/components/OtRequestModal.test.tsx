// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactElement } from 'react';
import { OtRequestModal } from '../../../src/components/employee/OtRequestModal';
import { requestService } from '../../../src/services/requestService';

// P1-009 (test-design-qa.md): first component/unit test suite for this project —
// covers zod-driven form validation and the isValid-derived submit button state
// on OtRequestModal (react-hook-form + zod, mode: 'onChange').

vi.mock('../../../src/services/requestService', () => ({
  requestService: {
    submitOtRequest: vi.fn(),
  },
}));

const TODAY = new Date().toISOString().split('T')[0];

function renderWithClient(ui: ReactElement) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>);
}

function renderModal(onSuccess = vi.fn(), onClose = vi.fn()) {
  renderWithClient(<OtRequestModal isOpen onClose={onClose} onSuccess={onSuccess} />);
  return { onSuccess, onClose };
}

async function fillValidForm() {
  const user = userEvent.setup();
  fireEvent.change(screen.getByLabelText('Ngày làm OT'), { target: { value: TODAY } });
  await user.clear(screen.getByLabelText(/Số giờ dự kiến/));
  await user.type(screen.getByLabelText(/Số giờ dự kiến/), '2');
  await user.type(screen.getByLabelText('Lý do'), 'Hỗ trợ triển khai hệ thống mới');
  return user;
}

describe('OtRequestModal', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('disables submit initially — empty required fields fail validation', () => {
    renderModal();

    expect(screen.getByRole('button', { name: /Gửi yêu cầu OT/ })).toBeDisabled();
  });

  it('shows a validation error and keeps submit disabled when reason is under 10 characters', async () => {
    const user = userEvent.setup();
    renderModal();

    fireEvent.change(screen.getByLabelText('Ngày làm OT'), { target: { value: TODAY } });
    await user.clear(screen.getByLabelText(/Số giờ dự kiến/));
    await user.type(screen.getByLabelText(/Số giờ dự kiến/), '2');
    await user.type(screen.getByLabelText('Lý do'), 'ngắn');

    expect(await screen.findByText('Lý do tối thiểu 10 ký tự')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Gửi yêu cầu OT/ })).toBeDisabled();
  });

  it('shows a validation error when hours exceed the 8-hour max', async () => {
    const user = userEvent.setup();
    renderModal();

    await user.clear(screen.getByLabelText(/Số giờ dự kiến/));
    await user.type(screen.getByLabelText(/Số giờ dự kiến/), '10');

    expect(await screen.findByText('Tối đa 8 giờ')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Gửi yêu cầu OT/ })).toBeDisabled();
  });

  it('shows a validation error when hours are below the 0.5-hour minimum', async () => {
    const user = userEvent.setup();
    renderModal();

    await user.clear(screen.getByLabelText(/Số giờ dự kiến/));
    await user.type(screen.getByLabelText(/Số giờ dự kiến/), '0');

    expect(await screen.findByText('Tối thiểu 0.5 giờ')).toBeInTheDocument();
  });

  it('enables submit once date, hours, and reason all satisfy validation (isValid-derived state)', async () => {
    renderModal();
    await fillValidForm();

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /Gửi yêu cầu OT/ })).toBeEnabled();
    });
  });

  it('submits the entered values and calls onSuccess', async () => {
    vi.mocked(requestService.submitOtRequest).mockResolvedValue({
      id: 'req-1',
      status: 'PENDING',
    } as never);
    const { onSuccess } = renderModal();
    const user = await fillValidForm();

    await waitFor(() => expect(screen.getByRole('button', { name: /Gửi yêu cầu OT/ })).toBeEnabled());
    await user.click(screen.getByRole('button', { name: /Gửi yêu cầu OT/ }));

    await waitFor(() => {
      expect(requestService.submitOtRequest).toHaveBeenCalledWith({
        plannedDate: TODAY,
        plannedOtHours: 2,
        reason: 'Hỗ trợ triển khai hệ thống mới',
      });
    });
    await waitFor(() => expect(onSuccess).toHaveBeenCalled());
  });

  it('renders nothing when isOpen is false', () => {
    renderWithClient(<OtRequestModal isOpen={false} onClose={vi.fn()} onSuccess={vi.fn()} />);

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });
});
