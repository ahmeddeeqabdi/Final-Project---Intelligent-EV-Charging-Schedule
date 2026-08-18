import type { ScheduleResult } from '@/types/api'

const download = (content: string, filename: string, type: string) => {
  const url = URL.createObjectURL(new Blob([content], { type }))
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  anchor.click()
  URL.revokeObjectURL(url)
}

const activeSlots = (result: ScheduleResult) =>
  result.slots.filter((slot) => slot.powerValue > 0).sort((a, b) => a.timestamp.localeCompare(b.timestamp))

export const downloadScheduleCsv = (result: ScheduleResult) => {
  const rows = [
    ['Time', 'Power (kW)', 'Price (DKK/kWh)', 'CO2 (g/kWh)'],
    ...result.slots.map((slot) => [slot.timestamp, slot.powerValue, slot.energyPrice, slot.co2Intensity]),
  ]
  download(rows.map((row) => row.join(',')).join('\n'), 'charging-schedule.csv', 'text/csv;charset=utf-8')
}

const icsDate = (date: Date) => date.toISOString().replace(/[-:]/g, '').replace(/\.\d{3}/, '')

export const downloadScheduleCalendar = (result: ScheduleResult) => {
  const slots = activeSlots(result)
  if (!slots.length) return
  const start = new Date(slots[0].timestamp)
  const end = new Date(new Date(slots[slots.length - 1].timestamp).getTime() + 60 * 60 * 1000)
  const content = [
    'BEGIN:VCALENDAR', 'VERSION:2.0', 'PRODID:-//EV Charging Scheduler//EN',
    'BEGIN:VEVENT', `UID:ev-charge-${start.getTime()}@scheduler`,
    `DTSTAMP:${icsDate(new Date())}`, `DTSTART:${icsDate(start)}`, `DTEND:${icsDate(end)}`,
    'SUMMARY:Charge your EV', `DESCRIPTION:${result.algorithm} charging plan`,
    'END:VEVENT', 'END:VCALENDAR',
  ].join('\r\n')
  download(content, 'ev-charging-plan.ics', 'text/calendar;charset=utf-8')
}

export const createChargingReminder = async (result: ScheduleResult): Promise<string> => {
  if (!('Notification' in window)) return 'Notifications are not supported by this browser.'
  const permission = Notification.permission === 'default'
    ? await Notification.requestPermission()
    : Notification.permission
  if (permission !== 'granted') return 'Notification permission was not granted.'

  const first = activeSlots(result)[0]
  if (!first) return 'This plan does not contain a charging session.'
  const delay = new Date(first.timestamp).getTime() - Date.now()
  if (delay <= 0) {
    new Notification('EV charging reminder', { body: 'Your planned charging window has started.' })
    return 'Charging reminder shown.'
  }
  if (delay > 2_147_483_647) return 'The charging window is too far away for a browser reminder.'
  window.setTimeout(() => new Notification('EV charging reminder', {
    body: 'Your planned charging window is starting now.',
  }), delay)
  return 'Reminder set. Keep this browser tab open.'
}
