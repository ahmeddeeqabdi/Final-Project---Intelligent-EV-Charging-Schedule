import { useEffect, useState, type ReactNode } from 'react'
import { MixerHorizontalIcon } from '@radix-ui/react-icons'
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
    <div className="grid gap-4 lg:grid-cols-[1fr_3fr] xl:grid-cols-[400px_1fr] lg:gap-8">
      <section className="lg:col-span-1">
        {isDesktop ? (
          <div className="sticky top-6">{constraints}</div>
        ) : (
          <Accordion type="single" collapsible defaultValue="constraints">
            <AccordionItem value="constraints" className="border border-border bg-card px-4 shadow-hard-sm">
              <AccordionTrigger className="min-h-11 py-3 text-sm font-semibold tracking-wide uppercase text-foreground">
                <span className="inline-flex items-center gap-2">
                  <MixerHorizontalIcon className="h-4 w-4" />
                  Charging preferences
                </span>
              </AccordionTrigger>
              <AccordionContent className="pb-4">{constraints}</AccordionContent>
            </AccordionItem>
          </Accordion>
        )}
      </section>
      <section className="lg:col-span-1 border-l-0 lg:border-l lg:border-border lg:pl-8">{content}</section>
    </div>
  )
}
