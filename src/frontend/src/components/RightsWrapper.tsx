import type { ReactNode } from 'react'
import type { RightDto } from '../dto/rights'
import { useRights } from '../queries/rights'

export default function RightsWrapper({
  requiredRights,
  children,
  fallback = null,
}: {
  requiredRights: readonly RightDto[]
  children: ReactNode
  fallback?: ReactNode
}) {
  const rights = useRights()
  return requiredRights.some((right) => rights.includes(right)) ? children : fallback
}
