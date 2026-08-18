import type { ReactNode } from 'react'

interface ResponsiveDashboardProps {
  constraints: ReactNode
  content: ReactNode
}

export function ResponsiveDashboard({ constraints, content }: ResponsiveDashboardProps) {
  return (
    <main className="grid items-start gap-6 lg:grid-cols-[minmax(340px,420px)_minmax(0,1fr)] lg:gap-8">
      <section aria-label="Charging preferences" className="lg:sticky lg:top-6">{constraints}</section>
      <section aria-label="Charging plan" className="min-w-0">{content}</section>
    </main>
  )
}
