import { Clock3, Download } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { useScheduleHistory } from '@/hooks/useScheduleHistory'
import { downloadScheduleCsv } from '@/lib/schedule-export'

const currency = new Intl.NumberFormat('en-DK', { style: 'currency', currency: 'DKK' })

export function ScheduleHistory() {
  const history = useScheduleHistory()
  if (!history.data?.length) return null

  return (
    <Card>
      <CardHeader className="pb-2">
        <CardTitle className="flex items-center gap-2 text-lg"><Clock3 className="h-5 w-5" />Recent plans</CardTitle>
      </CardHeader>
      <CardContent>
        <div className="grid gap-2 sm:grid-cols-2 xl:grid-cols-3">
          {history.data.slice(0, 6).map((item) => (
            <div key={item.id} className="border border-border bg-background p-3">
              <div className="flex items-start justify-between gap-2">
                <div>
                  <p className="font-bold capitalize">{item.algorithm}</p>
                  <p className="text-xs text-muted-foreground">{new Date(item.createdAt).toLocaleString()}</p>
                </div>
                <Button type="button" variant="ghost" className="min-h-9 px-2" title="Download CSV" onClick={() => downloadScheduleCsv(item)}>
                  <Download className="h-4 w-4" />
                </Button>
              </div>
              <p className="mt-2 text-sm">{currency.format(item.totalCost)} · {Math.round(item.totalCO2)} gCO2</p>
            </div>
          ))}
        </div>
      </CardContent>
    </Card>
  )
}
