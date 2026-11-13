import { isAxiosError } from 'axios'
import type { ListResponseDto } from '../dto/listResponse'
import type { RightDto, UserRightsDto } from '../dto/rights'
import { api } from './client'

export async function getUserRights(signal: AbortSignal): Promise<UserRightsDto[]> {
  const response = await api.get<ListResponseDto<UserRightsDto>>('/adminApi/v1/rights', { signal })
  return response.data.data
}

export async function changeUserRight(
  userId: string,
  right: RightDto,
  grant: boolean,
): Promise<void> {
  const url = `/adminApi/v1/rights/${encodeURIComponent(userId)}/${encodeURIComponent(right)}`
  if (grant) await api.put(url)
  else await api.delete(url)
}

export function rightsErrorMessage(error: unknown, fallback: string): string {
  if (isAxiosError<{ detail?: string }>(error)) {
    if (error.response?.status === 403) return 'You do not have permission to manage rights.'
    if (error.response?.status === 404) return 'This user no longer exists. Refresh the list.'
    const detail = error.response?.data?.detail
    if (typeof detail === 'string' && detail) return detail
  }
  return fallback
}
