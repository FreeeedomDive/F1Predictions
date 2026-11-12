import InfoOutlined from '@mui/icons-material/InfoOutlined'
import { IconButton, Tooltip } from '@mui/material'

export default function StatisticsInfo({
  title,
  description,
}: {
  title: string
  description: string
}) {
  return (
    <Tooltip title={description} arrow>
      <IconButton
        aria-label={`About ${title}`}
        sx={{
          p: 1,
          width: 30,
          height: 30,
          minWidth: 'auto',
          flexShrink: 0,
          borderRadius: '50%',
          bgcolor: 'transparent',
          color: 'text.secondary',
        }}
      >
        <InfoOutlined sx={{ fontSize: 18 }} />
      </IconButton>
    </Tooltip>
  )
}
