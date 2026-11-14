import {
  Box,
  Card,
  CardContent,
  Chip,
  Divider,
  Link,
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

const LAST_RULES_SEASON = 2027

function seasonRange(first: number, last: number) {
  return first === last ? `${first}` : `${first}–${last}`
}

function Rule({ children }: { children: ReactNode }) {
  return (
    <Typography variant="body1" sx={{ pl: 1 }}>
      {'• '}
      {children}
    </Typography>
  )
}

function SubRule({ children }: { children: ReactNode }) {
  return (
    <Typography variant="body2" color="text.secondary" sx={{ pl: 3.5 }}>
      {'◦ '}
      {children}
    </Typography>
  )
}

function PointsTable({
  rows,
  label,
  pointsLabel = 'Points',
}: {
  rows: [string, string][]
  label: string
  pointsLabel?: string
}) {
  return (
    <TableContainer>
      <Table size="small" sx={{ maxWidth: 560 }}>
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

function RuleCard({
  title,
  badge,
  badgeColor = 'success',
  from,
  to = LAST_RULES_SEASON,
  firstSeason,
  selectedSeason,
  children,
}: {
  title: string
  badge: string
  badgeColor?: 'success' | 'warning'
  from: number
  to?: number
  firstSeason: number
  selectedSeason: number
  children: ReactNode
}) {
  const first = Math.max(from, firstSeason)
  if (first > to) return null

  const active = selectedSeason >= first && selectedSeason <= to

  return (
    <Card variant="outlined" sx={{ bgcolor: active ? 'rgba(255,255,255,0.03)' : undefined }}>
      <CardContent>
        <Stack direction="row" sx={{ flexWrap: 'wrap', gap: 1, alignItems: 'center', mb: 1.5 }}>
          <Typography component="h3" variant="h6" sx={{ fontWeight: 600, flexGrow: 1 }}>
            {title}
          </Typography>
          <Chip label={badge} color={badgeColor} size="small" />
          <Chip
            label={seasonRange(first, to)}
            color={active ? 'success' : 'error'}
            size="small"
            variant="outlined"
          />
        </Stack>
        <Stack spacing={1}>{children}</Stack>
      </CardContent>
    </Card>
  )
}

export default function RaceRulebook({
  firstSeason,
  selectedSeason,
}: {
  firstSeason: number
  selectedSeason: number
}) {
  const seasons = { firstSeason, selectedSeason }
  const maximumPoints: { from: number; to: number; description: string }[] = [
    { from: 2023, to: 2023, description: '30 (10th place + First DNF)' },
    {
      from: 2024,
      to: 2025,
      description: '55 (10th place + DNF + Incidents + Winner’s gap + Team-mate battles)',
    },
    {
      from: 2026,
      to: 2027,
      description: '55 (10th place + DNF + Incidents + Winner’s gap + Driver position)',
    },
  ]

  return (
    <Stack spacing={4} sx={{ maxWidth: 860, pb: 3 }}>
      <Stack spacing={2}>
        <Typography component="h2" variant="h5" sx={{ fontWeight: 600 }}>
          Race predictions
        </Typography>
        <Typography>
          Before each race, fill in several prediction categories. Predictions close at the start of
          the race. All categories are independent and award points separately. Cards with a{' '}
          <Chip
            label="red"
            color="error"
            size="small"
            variant="outlined"
            sx={{ verticalAlign: 'middle', mx: 0.3 }}
          />{' '}
          season badge do not apply to the selected season ({selectedSeason}). Rules are shown from
          this league’s first season ({firstSeason}) through 2027.
        </Typography>

        <RuleCard title="10th place" badge="Up to 25 points" from={2023} {...seasons}>
          <Rule>Choose the driver who will finish in 10th place.</Rule>
          <Rule>
            Points depend on how far the selected driver finishes from 10th place in the final
            classification.
          </Rule>
          <PointsTable
            label="Selected driver’s final position"
            rows={[
              ['10th (exact)', '25'],
              ['9th or 11th', '18'],
              ['8th or 12th', '15'],
              ['7th or 13th', '12'],
              ['6th or 14th', '10'],
              ['5th or 15th', '8'],
              ['4th or 16th', '6'],
              ['3rd or 17th', '4'],
              ['2nd or 18th', '2'],
              ['1st, 19th or 20th', '1'],
              ['21st or lower', '0'],
            ]}
          />
        </RuleCard>

        <RuleCard
          title="First DNF"
          badge="5 points"
          badgeColor="warning"
          from={2023}
          to={2023}
          {...seasons}
        >
          <Rule>
            In 2023, participants chose one driver who would be the first to retire. A correct
            prediction earned <strong>5 points</strong>.
          </Rule>
        </RuleCard>

        <RuleCard title="DNF (Did Not Finish)" badge="Up to 10 points" from={2024} {...seasons}>
          <Rule>
            Choose <strong>5 drivers</strong> who will retire from the race, or select “Nobody”.
          </Rule>
          <Rule>
            Each correctly predicted DNF driver among your 5 choices earns <strong>2 points</strong>
            .
          </Rule>
          <Rule>
            If you select “Nobody” and there are no retirements, you earn <strong>10 points</strong>
            .
          </Rule>
          <Rule>
            If you select “Nobody” but at least one driver retires, you earn{' '}
            <strong>0 points</strong>.
          </Rule>
          <Rule>Duplicate drivers are not allowed: the list must contain 5 different drivers.</Rule>
          <Divider sx={{ my: 0.5 }} />
          <Typography variant="body2" sx={{ pl: 1, fontWeight: 600, color: 'warning.main' }}>
            Important: what counts as a DNF
          </Typography>
          <Rule>
            Only drivers who <strong>started the race</strong> but did not finish it count.
          </Rule>
          <SubRule>
            A driver who did not take part in the formation lap and did not take their place on the
            starting grid is <strong>DNS</strong>, not DNF.
          </SubRule>
          <SubRule>
            A driver who completed the formation lap but could not take the race start (for example,
            stalled on the grid) is <strong>DNS</strong>, not DNF.
          </SubRule>
          <SubRule>
            A disqualified driver, whether disqualified during the race or after the finish, is{' '}
            <strong>DQ</strong>, not DNF.
          </SubRule>
          <SubRule>
            A driver who started the race but failed to reach the finish for any reason is{' '}
            <strong>DNF</strong>.
          </SubRule>
          <Rule>
            In any disputed case, the driver’s status is determined by the official{' '}
            <Link href="#useful-links" underline="hover" color="inherit">
              <strong>Final Race Classification</strong>
            </Link>{' '}
            document: a <strong>DNF</strong> status counts as a DNF; any other status (DNS, DQ,
            etc.) does not.
          </Rule>
        </RuleCard>

        <RuleCard
          title="Incidents (Safety Car / VSC / Red Flag)"
          badge="5 points"
          from={2024}
          {...seasons}
        >
          <Rule>
            Choose one of four values: <strong>0</strong>, <strong>1</strong>, <strong>2</strong> or{' '}
            <strong>3+</strong>.
          </Rule>
          <Rule>
            An incident is one continuous episode involving a Virtual Safety Car (VSC), Safety Car
            (SC) or red flag, lasting until normal racing resumes. A yellow flag alone does not
            count.
          </Rule>
          <Rule>
            Count incidents, not individual flags or deployments. Consecutive transitions between
            VSC, SC and red flags before normal racing resumes count as{' '}
            <strong>one incident</strong>.
          </Rule>
          <SubRule>
            An accident followed by a yellow flag, then VSC, then SC and finally a restart is{' '}
            <strong>one incident</strong>.
          </SubRule>
          <SubRule>
            If an VSC/SC period is followed by a red flag before racing resumes, the whole sequence
            still counts as <strong>one incident</strong>.
          </SubRule>
          <Rule>
            If your answer exactly matches the actual number of incidents, you earn{' '}
            <strong>5 points</strong>; otherwise, <strong>0</strong>.
          </Rule>
        </RuleCard>

        <RuleCard title="Winner’s gap" badge="5 points" from={2024} to={2026} {...seasons}>
          <Rule>
            Enter the gap in seconds between 1st and 2nd place at the finish. Decimal values are
            allowed.
          </Rule>
          <Rule>
            The participant whose prediction has the <strong>smallest difference</strong> from the
            actual gap earns <strong>5 points</strong>. Everyone else earns 0.
          </Rule>
          <Rule>If several participants are equally close, they all earn 5 points.</Rule>
        </RuleCard>

        <RuleCard title="Winner’s gap" badge="Up to 5 points" from={2027} {...seasons}>
          <Rule>
            Enter the gap in seconds between 1st and 2nd place at the finish. Decimal values are
            allowed.
          </Rule>
          <Rule>
            In 2027, points depend on the absolute error in your prediction:{' '}
            <strong>x = |predicted gap − actual gap|</strong>, in seconds.
          </Rule>
          <Rule>
            Each prediction is scored independently. Your points do not depend on other
            participants’ predictions or on how many participants are in the league.
          </Rule>
          <PointsTable
            label="Absolute error x (seconds)"
            rows={[
              ['0 ≤ x ≤ 0.5', '5'],
              ['0.5 < x ≤ 2', '4'],
              ['2 < x ≤ 3.5', '3'],
              ['3.5 < x ≤ 5', '2'],
              ['5 < x ≤ 7', '1'],
              ['x > 7', '0'],
            ]}
          />
          <Rule>
            Points are based on the exact difference between your prediction and the actual gap,
            without rounding.
          </Rule>
        </RuleCard>

        <RuleCard title="Driver position" badge="Up to 10 points" from={2026} {...seasons}>
          <Rule>
            A specific driver is selected for each race. Your task is to predict their final
            position, from 1 to the number of drivers in that race (22 for a full 22-driver grid).
          </Rule>
          <PointsTable
            label="Difference from the actual position"
            rows={[
              ['Exact (±0)', '10'],
              ['±1 position', '7'],
              ['±2 positions', '5'],
              ['±3 positions', '3'],
              ['±4 positions', '2'],
              ['±5 positions', '1'],
              ['±6 or more', '0'],
            ]}
          />
        </RuleCard>

        <RuleCard
          title="Teams (team-mate battles)"
          badge="Up to 10 points"
          from={2024}
          to={2025}
          {...seasons}
        >
          <Rule>
            For each team, choose the driver who will finish{' '}
            <strong>ahead of their team-mate</strong> in the final classification.
          </Rule>
          <Rule>Each correctly predicted team-mate battle earns 1 point.</Rule>
        </RuleCard>

        <RuleCard title="Sprint races" badge="×1.00" from={2024} to={2024} {...seasons}>
          <Rule>In 2024, sprint races awarded the full number of points.</Rule>
        </RuleCard>

        <RuleCard title="Sprint races" badge="×0.30" badgeColor="warning" from={2025} {...seasons}>
          <Rule>
            Sprint races count with a <strong>×0.30</strong> multiplier
            applied to the normal points total.
          </Rule>
          <Rule>
            The multiplier is applied after adding the category points. Any fractional part is
            discarded: for example, 49 × 0.30 = 14.7 gives <strong>14 points</strong>.
          </Rule>
          <Rule>Regular races on the same race weekend award the full number of points.</Rule>
        </RuleCard>

        <Card variant="outlined">
          <CardContent>
            <Typography component="h3" variant="h6" sx={{ fontWeight: 600, mb: 1.5 }}>
              Maximum points per race
            </Typography>
            <PointsTable
              label="Season"
              pointsLabel="Maximum per race"
              rows={maximumPoints
                .filter(({ to }) => to >= firstSeason)
                .map(({ from, to, description }) => [
                  seasonRange(Math.max(from, firstSeason), to),
                  description,
                ])}
            />
          </CardContent>
        </Card>
      </Stack>

      <Divider />

      <Box id="useful-links" sx={{ scrollMarginTop: 24 }}>
        <Typography component="h2" variant="h5" sx={{ fontWeight: 600, mb: 2 }}>
          Useful links
        </Typography>
        <Card variant="outlined">
          <CardContent>
            <Stack spacing={2}>
              <Stack spacing={0.5}>
                <Typography sx={{ fontWeight: 600 }}>Official FIA documents</Typography>
                <Link
                  href="https://www.fia.com/documents/championships/fia-formula-one-world-championship-14"
                  target="_blank"
                  rel="noopener noreferrer"
                  underline="hover"
                >
                  fia.com — FIA Formula One World Championship documents
                </Link>
                <Typography variant="body2" color="text.secondary">
                  Official championship documents, including the{' '}
                  <strong>Final Race Classification</strong> for each race.
                </Typography>
              </Stack>
              <Divider />
              <Stack spacing={0.5}>
                <Typography sx={{ fontWeight: 600 }}>Liquipedia F1</Typography>
                <Link
                  href="https://liquipedia.net/formula1/Main_Page"
                  target="_blank"
                  rel="noopener noreferrer"
                  underline="hover"
                >
                  liquipedia.net/formula1
                </Link>
                <Typography variant="body2" color="text.secondary">
                  A Formula 1 wiki covering the season schedule, race results, team lineups and
                  championship history.
                </Typography>
              </Stack>
            </Stack>
          </CardContent>
        </Card>
      </Box>
    </Stack>
  )
}
