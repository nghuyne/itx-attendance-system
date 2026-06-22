import api from './api';
import type { CreateDepartmentRequest, DepartmentDto, EmployeeWithDeptDto } from '../types/api';

export const departmentService = {
  getAll: (): Promise<DepartmentDto[]> =>
    api.get<DepartmentDto[]>('/admin/departments').then(r => r.data),

  create: (data: CreateDepartmentRequest): Promise<DepartmentDto> =>
    api.post<DepartmentDto>('/admin/departments', data).then(r => r.data),

  update: (id: number, data: CreateDepartmentRequest): Promise<DepartmentDto> =>
    api.put<DepartmentDto>(`/admin/departments/${id}`, data).then(r => r.data),

  delete: (id: number): Promise<void> =>
    api.delete(`/admin/departments/${id}`).then(() => {}),

  assignShift: (deptId: number, shiftId: string): Promise<{ updatedCount: number }> =>
    api.put<{ updatedCount: number }>(`/admin/departments/${deptId}/shift`, { shiftId }).then(r => r.data),

  getEmployeesWithDept: (): Promise<EmployeeWithDeptDto[]> =>
    api.get<EmployeeWithDeptDto[]>('/admin/employees/details').then(r => r.data),

  assignEmployeeDepartment: (userId: string, departmentId: number | null): Promise<EmployeeWithDeptDto> =>
    api.put<EmployeeWithDeptDto>(`/admin/employees/${userId}/department`, { departmentId }).then(r => r.data),
};
