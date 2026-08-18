import { useEffect, useState } from 'react'
import { Check, CircleDashed } from 'lucide-react'
import { Card, CardContent } from '@/components/ui/card'
import { Spinner } from '@/components/ui/spinner'

const stages = ['Loading electricity prices', 'Comparing charging strategies', 'Building your recommended plan']

export function ScheduleLoadingState() {
  const [activeStage, setActiveStage] = useState(0)

  useEffect(() => {
    const timers = [
      window.setTimeout(() => setActiveStage(1), 900),
      window.setTimeout(() => setActiveStage(2), 2200),
    ]
    return () => timers.forEach(window.clearTimeout)
  }, [])

  return (
    <Card aria-live="polite">
      <CardContent className="p-6 sm:p-8">
        <div className="flex items-center gap-3"><Spinner className="h-5 w-5 text-primary" /><div><p className="font-semibold">Creating your charging plan</p><p className="text-sm text-muted-foreground">Usually ready in a few seconds.</p></div></div>
        <ol className="mt-6 grid gap-3 sm:grid-cols-3">
          {stages.map((stage, index) => <li key={stage} className={`flex items-center gap-2 rounded-md p-3 text-sm ${index === activeStage ? 'bg-primary/10 font-semibold text-foreground' : index < activeStage ? 'text-success' : 'text-muted-foreground'}`}>{index < activeStage ? <Check className="h-4 w-4" /> : <CircleDashed className={`h-4 w-4 ${index === activeStage ? 'animate-spin text-primary' : ''}`} />}{stage}</li>)}
        </ol>
      </CardContent>
    </Card>
  )
}
