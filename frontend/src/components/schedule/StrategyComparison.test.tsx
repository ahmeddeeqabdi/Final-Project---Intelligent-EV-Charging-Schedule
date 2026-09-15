import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import type { OptimizationAlgorithm, ScheduleResult } from '@/types/api'
import { StrategyComparison } from './StrategyComparison'

const result = (
  algorithm: OptimizationAlgorithm,
  totalCost: number,
  totalCO2: number,
): ScheduleResult => ({
  algorithm,
  totalCost,
  totalCO2,
  isDegradedMode: false,
  fallbackSource: 'live',
  slots: [],
  marketSignals: [],
})

describe('StrategyComparison', () => {
  it('hides comparison controls when only one result exists', () => {
    const { container } = render(
      <StrategyComparison
        comparisons={{ greedy: result('greedy', 10, 1_000) }}
        active="greedy"
        costWeight={0.5}
        onSelect={() => undefined}
      />,
    )

    expect(container).toBeEmptyDOMElement()
  })

  it('renders alternatives and reports the selected strategy', async () => {
    const onSelect = vi.fn()
    const user = userEvent.setup()
    render(
      <StrategyComparison
        comparisons={{
          greedy: result('greedy', 10, 1_000),
          optimal: result('optimal', 9, 900),
        }}
        active="greedy"
        costWeight={0.5}
        onSelect={onSelect}
      />,
    )

    await user.click(screen.getByRole('button', { name: /Best overall balance/i }))

    expect(onSelect).toHaveBeenCalledWith('optimal')
    expect(screen.getByText('Recommended')).toBeInTheDocument()
  })
})
