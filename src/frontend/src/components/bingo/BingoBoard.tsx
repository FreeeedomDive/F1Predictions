import InfoOutlined from '@mui/icons-material/InfoOutlined'
import { Box, IconButton, Tooltip, Typography } from '@mui/material'
import type { BingoBoardDto, BingoCardProbabilityDto } from '../../dto/bingo'

const probabilityColors = {
  LOW: 'darkred',
  MEDIUM: '#F4D06F',
  HIGH: 'darkgreen',
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
      {board.cards.map((card) => (
        <Box component="li" key={card.id} sx={{ display: 'flex', minWidth: 0 }}>
          <Box
            component="article"
            aria-label={`${card.description}. ${card.completed ? 'Completed. ' : ''}Progress ${card.completedRepeats} of ${card.requiredRepeats}. Probability ${card.probability.toLowerCase()}.`}
            sx={{
              position: 'relative',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              width: '100%',
              minWidth: 0,
              minHeight: { xs: 120, sm: 132, md: 152 },
              px: { xs: 0.5, sm: 2 },
              pt: { xs: 3, sm: 4 },
              pb: { xs: 1, sm: 3 },
              border: '4px solid',
              borderColor: probabilityColors[card.probability],
              borderRadius: 2,
              textAlign: 'center',
              overflow: 'hidden',
            }}
          >
            <Box sx={{ minWidth: 0 }}>
              <Typography
                component="span"
                sx={{
                  fontSize: { xs: 10, sm: 12, md: 14 },
                  fontWeight: 'bold',
                  overflowWrap: 'anywhere',
                }}
              >
                {card.description}
              </Typography>
              {card.requiredRepeats > 1 && (
                <Box
                  role="img"
                  aria-label={`Progress: ${card.completedRepeats} of ${card.requiredRepeats}`}
                  sx={{
                    display: 'flex',
                    justifyContent: 'center',
                    flexWrap: 'wrap',
                    gap: 0.5,
                    mt: 1,
                  }}
                >
                  {Array.from({ length: card.requiredRepeats }, (_, index) => (
                    <Box
                      key={index}
                      sx={{
                        width: { xs: 5, sm: 12, md: 24, lg: 32 },
                        height: { xs: 5, sm: 12, md: 24, lg: 32 },
                        borderRadius: '50%',
                        border: '1px solid gray',
                        bgcolor: index < card.completedRepeats ? 'gray' : 'transparent',
                      }}
                    />
                  ))}
                </Box>
              )}
            </Box>
            {card.completed && (
              <Box
                aria-hidden="true"
                sx={{
                  position: 'absolute',
                  inset: 0,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  pointerEvents: 'none',
                  '&::before, &::after': {
                    content: '""',
                    position: 'absolute',
                    width: '150%',
                    height: 4,
                    bgcolor: probabilityColors[card.probability],
                  },
                  '&::before': { transform: 'rotate(45deg)' },
                  '&::after': { transform: 'rotate(-45deg)' },
                }}
              />
            )}
            {card.explanation && (
              <Tooltip title={card.explanation} describeChild arrow>
                <IconButton
                  size="small"
                  aria-label={`Explanation: ${card.description}`}
                  sx={{
                    position: 'absolute',
                    top: { xs: 1, sm: 8 },
                    right: { xs: 1, sm: 8 },
                    p: 0.5,
                  }}
                >
                  <InfoOutlined sx={{ fontSize: { xs: 14, sm: 18 } }} />
                </IconButton>
              </Tooltip>
            )}
          </Box>
        </Box>
      ))}
    </Box>
  )
}
