import { isAxiosError } from 'axios'
import type { MeResponseDto } from '../dto/user'
import { api } from './client'

export function isUnauthorized(error: unknown): boolean {
  return isAxiosError(error) && error.response?.status === 401
}

export async function authenticate(token: string, signal: AbortSignal): Promise<void> {
  await api.post('/api/v1/auth', { token }, { signal })
}

export async function logout(): Promise<void> {
  await api.delete('/api/v1/auth')
}

export async function getMe(signal?: AbortSignal): Promise<MeResponseDto | null> {
  try {
    const response = await api.get<MeResponseDto>('/api/v1/me', { signal })
    return response.data
  } catch (error) {
    if (isUnauthorized(error)) return null
    throw error
  }
}
