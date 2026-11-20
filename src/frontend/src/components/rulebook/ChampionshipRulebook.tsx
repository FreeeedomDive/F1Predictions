import { Stack, Typography } from '@mui/material'
import RulebookCard, { PointsTable, Rule } from './RulebookCard'

function pointsRows(points: number[]): [string, string][] {
  return [
    ...points.map<[string, string]>((score, difference) => [
      difference === 0 ? '0 (exact)' : String(difference),
      String(score),
    ]),
    [`${points.length} or more`, '0'],
  ]
}

export default function ChampionshipRulebook({
  season,
  rulesVersion,
}: {
  season: number
  rulesVersion: string | null | undefined
}) {
  const referenceVersion =
    season <= 2026 ? 'championship-v2026' : season === 2027 ? 'championship-v2027' : null
  const displayedVersion = rulesVersion ?? referenceVersion
  const hasKnownVersion =
    displayedVersion === 'championship-v2026' || displayedVersion === 'championship-v2027'
  const pointsCard = {
    seasonLabel: hasKnownVersion ? String(season) : undefined,
    active: hasKnownVersion,
  }

  return (
    <Stack
      component="section"
      aria-label="Championship predictions"
      spacing={2}
      sx={{ maxWidth: 860 }}
    >
      <Typography component="h2" variant="h5" sx={{ fontWeight: 600 }}>
        Championship predictions
      </Typography>
      {!rulesVersion && (
        <Typography variant="body2" color="text.secondary">
          Rules are shown for reference until a version is available for the selected season.
        </Typography>
      )}
      <Typography>
        Rank the drivers twice: before the season and again at mid-season. You can save or edit only
        the open stage. Each stage starts scoring when it closes, and the championship total is the
        sum of the two stages.
      </Typography>
      <Typography>
        Scores show what you would earn if the season ended with the current standings. Points
        depend on the absolute difference between the predicted and actual positions. A driver
        without a position in the standings used for scoring earns no points.
      </Typography>
      <RulebookCard title="Pre-season" badge="Up to 20 points per driver" {...pointsCard}>
        <Rule>Rank the eligible drivers in your predicted finishing order before the season.</Rule>
        <PointsTable
          label="Position difference"
          tableLabel="Pre-season championship points"
          rows={pointsRows([20, 14, 11, 8, 5, 2])}
        />
      </RulebookCard>
      <RulebookCard title="Mid-season" badge="Up to 10 points per driver" {...pointsCard}>
        <Rule>Rank the eligible drivers again for the second stage.</Rule>
        <Rule>
          If you do not save a mid-season prediction, your pre-season order, if any, is copied when
          the mid-season stage closes and scored using this table. Your own mid-season prediction
          always takes priority.
        </Rule>
        <PointsTable
          label="Position difference"
          tableLabel="Mid-season championship points"
          rows={pointsRows([10, 6, 4, 2, 1])}
        />
      </RulebookCard>
      <RulebookCard
        title="Full official standings"
        seasonLabel="2026"
        active={hasKnownVersion ? displayedVersion === 'championship-v2026' : undefined}
      >
        <Rule>
          Scoring uses the full official standings. A driver who joins after predictions open can
          shift the positions of other drivers.
        </Rule>
      </RulebookCard>
      <RulebookCard
        title="Eligible drivers only"
        seasonLabel="2027"
        active={hasKnownVersion ? displayedVersion === 'championship-v2027' : undefined}
      >
        <Rule>
          The eligible driver list is locked when each stage opens. Drivers outside it remain in the
          official standings but do not shift positions for scoring. For example, the driver in 22nd
          officially is scored as 20th if two drivers above them are outside that stage’s list.
        </Rule>
        <Rule>
          A new driver can be eligible for mid-season without affecting pre-season scoring. A copied
          prediction keeps its original order; any driver outside the mid-season list earns zero
          points.
        </Rule>
      </RulebookCard>
    </Stack>
  )
}
