import type {
  RaceClassificationEntryDto,
  RaceResultEditorDto,
  RaceResultValuesDto,
} from '../../dto/raceResult'

export interface ResultDraft {
  classification: RaceClassificationEntryDto[]
  incidents: string
  lead: string
}

export function resultDraft(page: RaceResultEditorDto): ResultDraft {
  const saved = page.result?.draft ?? page.result?.result
  return {
    classification:
      saved?.classification?.map((entry) => ({ ...entry })) ??
      page.teams.flatMap((team) =>
        team.drivers.map((driverName) => ({ driverName, status: 'FINISHED' as const })),
      ),
    incidents: saved == null ? '0' : saved.safetyCars == null ? '' : String(saved.safetyCars),
    lead: saved?.firstPlaceLead == null ? '' : String(saved.firstPlaceLead),
  }
}

export function resultValues(draft: ResultDraft, season: number): RaceResultValuesDto {
  return {
    classification: draft.classification,
    safetyCars: season >= 2024 && draft.incidents.trim() ? Number(draft.incidents) : null,
    firstPlaceLead:
      season >= 2024 && draft.lead.trim() ? Number(draft.lead.replace(',', '.')) : null,
  }
}

export function resultSignature(values: RaceResultValuesDto | null | undefined) {
  return JSON.stringify({
    classification: values?.classification ?? null,
    safetyCars: values?.safetyCars ?? null,
    firstPlaceLead: values?.firstPlaceLead ?? null,
  })
}
