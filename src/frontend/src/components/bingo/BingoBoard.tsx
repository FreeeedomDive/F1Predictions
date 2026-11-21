import CheckCircleRounded from '@mui/icons-material/CheckCircleRounded'
import InfoOutlined from '@mui/icons-material/InfoOutlined'
import { Box, IconButton, LinearProgress, Stack, Tooltip, Typography } from '@mui/material'
import type { BingoBoardDto, BingoCardProbabilityDto } from '../../dto/bingo'

const probabilityColors = {
  LOW: 'error.main',
  MEDIUM: 'warning.main',
  HIGH: 'success.main',
} satisfies Record<BingoCardProbabilityDto, string>

export default function BingoBoard({ board }: { board: BingoBoardDto }) {
  return (
    <Box
      component="ol"
      role="list"
      aria-label="Your bingo board"
      sx={{
        display: 'grid',
        gridTemplateColumns: 'repeat(5, minmax(0, 1fr))',
        gridAutoRows: '1fr',
        gap: { xs: 0.75, sm: 1.5 },
        listStyle: 'none',
        m: 0,
        p: 0,
      }}
    >
      {board.cards.map((card, index) => (
        <Box component="li" key={card.id} sx={{ display: 'flex', minWidth: 0 }}>
          <Box
            component="article"
            aria-label={`Card ${index + 1}: ${card.description}. ${card.completed ? 'Completed. ' : ''}Progress ${card.completedRepeats} of ${card.requiredRepeats}. Probability ${card.probability.toLowerCase()}.`}
            sx={{
              display: 'flex',
              width: '100%',
              minWidth: 0,
              minHeight: { xs: 108, sm: 132, md: 152 },
              p: { xs: 0.75, sm: 1.5 },
              border: 2,
              borderColor: probabilityColors[card.probability],
              borderRadius: 1.5,
              bgcolor: card.completed ? 'rgba(46, 125, 50, 0.16)' : 'background.paper',
              textAlign: 'center',
              alignItems: 'stretch',
            }}
          >
            <Stack spacing={0.75} sx={{ width: '100%', minWidth: 0 }}>
              <Stack
                direction="row"
                sx={{
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  color: 'text.secondary',
                }}
              >
                <Stack direction="row" sx={{ alignItems: 'center', gap: 0.25 }}>
                  <Typography variant="caption" sx={{ fontSize: { xs: 10, sm: 12 } }}>
                    {index + 1}
                  </Typography>
                  {card.completed && (
                    <CheckCircleRounded color="success" sx={{ fontSize: { xs: 12, sm: 18 } }} />
                  )}
                </Stack>
                {card.explanation && (
                  <Tooltip title={card.explanation} describeChild arrow>
                    <IconButton
                      size="small"
                      aria-label={`Explanation for card ${index + 1}`}
                      sx={{ p: 0.25 }}
                    >
                      <InfoOutlined sx={{ fontSize: { xs: 16, sm: 18 } }} />
                    </IconButton>
                  </Tooltip>
                )}
              </Stack>
              <Typography
                component="span"
                sx={{
                  flexGrow: 1,
                  fontSize: { xs: 11, sm: 13, md: 14 },
                  fontWeight: 600,
                  lineHeight: 1.35,
                  overflowWrap: 'anywhere',
                }}
              >
                {card.description}
              </Typography>
              <Typography component="span" variant="caption" sx={{ fontSize: { xs: 10, sm: 12 } }}>
                {card.completedRepeats} / {card.requiredRepeats}
              </Typography>
              <LinearProgress
                variant="determinate"
                value={(100 * card.completedRepeats) / card.requiredRepeats}
                color={card.completed ? 'success' : 'primary'}
                aria-label={`Progress for card ${index + 1}`}
                sx={{ height: 4, borderRadius: 1 }}
              />
            </Stack>
          </Box>
        </Box>
      ))}
    </Box>
  )
}
