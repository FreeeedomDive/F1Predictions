import {
  Card,
  CardContent,
  Chip,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material'
import type { ReactNode } from 'react'

export function Rule({ children }: { children: ReactNode }) {
  return (
    <Typography variant="body1" sx={{ pl: 1 }}>
      {'• '}
      {children}
    </Typography>
  )
}

export function PointsTable({
  rows,
  label,
  pointsLabel = 'Points',
  tableLabel,
}: {
  rows: [string, string][]
  label: string
  pointsLabel?: string
  tableLabel?: string
}) {
  return (
    <TableContainer>
      <Table size="small" aria-label={tableLabel} sx={{ maxWidth: 560 }}>
        <TableHead>
          <TableRow>
            <TableCell sx={{ fontWeight: 600 }}>{label}</TableCell>
            <TableCell sx={{ fontWeight: 600 }}>{pointsLabel}</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {rows.map(([description, points]) => (
            <TableRow key={description}>
              <TableCell>{description}</TableCell>
              <TableCell>{points}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  )
}

export default function RulebookCard({
  title,
  badge,
  badgeColor = 'success',
  seasonLabel,
  active,
  children,
}: {
  title: string
  badge?: string
  badgeColor?: 'success' | 'warning'
  seasonLabel?: string
  active?: boolean
  children: ReactNode
}) {
  return (
    <Card variant="outlined" sx={{ bgcolor: active ? 'rgba(255,255,255,0.03)' : undefined }}>
      <CardContent>
        <Stack direction="row" sx={{ flexWrap: 'wrap', gap: 1, alignItems: 'center', mb: 1.5 }}>
          <Typography component="h3" variant="h6" sx={{ fontWeight: 600, flexGrow: 1 }}>
            {title}
          </Typography>
          {badge && <Chip label={badge} color={badgeColor} size="small" />}
          {seasonLabel && (
            <Chip
              label={seasonLabel}
              color={active === undefined ? 'default' : active ? 'success' : 'error'}
              size="small"
              variant="outlined"
            />
          )}
        </Stack>
        <Stack spacing={1}>{children}</Stack>
      </CardContent>
    </Card>
  )
}
