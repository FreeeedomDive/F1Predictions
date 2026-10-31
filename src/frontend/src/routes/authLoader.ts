import { replace } from 'react-router'
import type { LoaderFunctionArgs } from 'react-router'
import { authenticate, isUnauthorized } from '../api/session'
import { queryClient } from '../app/queryClient'
import { meQueryOptions } from '../queries/me'

export interface AuthPageData {
  status: 'sign-in' | 'invalid-link' | 'unavailable' | 'cookie-unavailable'
}

export async function authLoader({
  request,
}: LoaderFunctionArgs): Promise<AuthPageData | Response> {
  const url = new URL(request.url)
  const token = url.searchParams.get('token')

  if (token !== null) {
    url.searchParams.delete('token')
    // Remove the credential from browser history before making any API requests.
    window.history.replaceState(window.history.state, '', url.pathname + url.search + url.hash)

    if (token.trim() === '') return { status: 'invalid-link' }

    try {
      await queryClient.cancelQueries()
      await authenticate(token, request.signal)
      queryClient.clear()
      const profile = await queryClient.fetchQuery({ ...meQueryOptions, staleTime: 0 })
      return profile ? replace('/') : { status: 'cookie-unavailable' }
    } catch (error) {
      if (request.signal.aborted) throw error
      return { status: isUnauthorized(error) ? 'invalid-link' : 'unavailable' }
    }
  }

  try {
    const profile = await queryClient.fetchQuery({ ...meQueryOptions, staleTime: 0 })
    return profile ? replace('/') : { status: 'sign-in' }
  } catch (error) {
    if (request.signal.aborted) throw error
    return { status: 'unavailable' }
  }
}
