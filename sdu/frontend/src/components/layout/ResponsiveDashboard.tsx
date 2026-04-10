import { useEffect, useState, type ReactNode } from 'react'
import { SlidersHorizontal } from 'lucide-react'
import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from '@/components/ui/accordion'

interface ResponsiveDashboardProps {
  constraints: ReactNode
  content: ReactNode
}

export function ResponsiveDashboard({ constraints, content }: ResponsiveDashboardProps) {
  const [isDesktop, setIsDesktop] = useState<boolean>(() => {
    if (typeof window === 'undefined') {
      return true
    }

    return window.innerWidth >= 1024
  })

  useEffect(() => {
    const onResize = () => {
      setIsDesktop(window.innerWidth >= 1024)
    }

    window.addEventListener('resize', onResize)
    return () => window.removeEventListener('resize', onResize)
  }, [])

  return (
    <div className="grid gap-4 lg:grid-cols-3 lg:gap-6">
      <section className="lg:col-span-1">
        {isDesktop ? (
          <div className="sticky top-6">{constraints}</div>
        ) : (
          <Accordion type="single" collapsible defaultValue="constraints">
            <AccordionItem value="constraints" className="rounded-lg border bg-card/80 px-4 shadow-soft">
              <AccordionTrigger className="min-h-11 py-3 text-sm font-semibold text-foreground">
                <span className="inline-flex items-center gap-2">
                  <SlidersHorizontal className="h-4 w-4" />
                  Charging Constraints
                </span>
              </AccordionTrigger>
              <AccordionContent className="pb-4">{constraints}</AccordionContent>
            </AccordionItem>
          </Accordion>
        )}
      </section>
      <section className="lg:col-span-2">{content}</section>
    </div>
  )
}
