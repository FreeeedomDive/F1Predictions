import { Box, Card, CardContent, Divider, Link, Stack, Typography } from '@mui/material'

export default function RulebookLinks() {
  return (
    <Box
      component="section"
      aria-labelledby="useful-links-heading"
      id="useful-links"
      sx={{ maxWidth: 860, scrollMarginTop: 24, pb: 3 }}
    >
      <Divider sx={{ mb: 3 }} />
      <Typography
        id="useful-links-heading"
        component="h2"
        variant="h5"
        sx={{ fontWeight: 600, mb: 2 }}
      >
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
  )
}
