import { Link, NavLink } from 'react-router-dom'
import { LogOut, Moon, ShieldCheck, Sun } from 'lucide-react'
import { BrandMark } from '@/components/layout/BrandMark'
import { Button } from '@/components/ui/button'
import { useAuth } from '@/hooks/useAuth'
import { useTheme } from '@/hooks/useTheme'
import { cn } from '@/lib/utils'

const navClass = ({ isActive }: { isActive: boolean }) =>
  cn(
    'inline-flex min-h-10 items-center gap-2 rounded-md px-3 text-sm font-semibold transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary',
    isActive ? 'bg-secondary text-foreground' : 'text-muted-foreground hover:bg-secondary hover:text-foreground',
  )

export function AppHeader() {
  const { user, logout } = useAuth()
  const { theme, toggle } = useTheme()
  const themeLabel = theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'
  const initial = user?.email?.charAt(0).toUpperCase() ?? '?'

  return (
    <header className="sticky top-0 z-40 border-b border-border bg-background/85 backdrop-blur supports-[backdrop-filter]:bg-background/70">
      <div className="mx-auto flex h-16 max-w-screen-2xl items-center gap-3 px-4 sm:px-6 lg:px-10">
        <Link to="/" className="rounded-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary" aria-label="EV Charging Scheduler home">
          <BrandMark className="hidden sm:inline-flex" />
          <BrandMark className="sm:hidden" compact />
        </Link>

        {user?.role === 'ADMIN' ? (
          <nav aria-label="Main" className="ml-2 flex items-center gap-1">
            <NavLink to="/" end className={navClass}>Planner</NavLink>
            <NavLink to="/admin" className={navClass}><ShieldCheck className="h-4 w-4" />Admin</NavLink>
          </nav>
        ) : null}

        <div className="ml-auto flex items-center gap-2">
          <span className="hidden items-center gap-2 text-sm text-muted-foreground md:inline-flex" title={user?.email}>
            <span className="grid h-8 w-8 place-items-center rounded-full bg-secondary font-semibold text-foreground" aria-hidden="true">{initial}</span>
            <span className="max-w-[16rem] truncate">{user?.email}</span>
          </span>
          <Button type="button" variant="ghost" className="w-11 px-0" onClick={toggle} aria-label={themeLabel} title={themeLabel}>
            {theme === 'dark' ? <Sun className="h-5 w-5" /> : <Moon className="h-5 w-5" />}
          </Button>
          <Button type="button" variant="secondary" onClick={logout} className="px-3">
            <LogOut className="h-4 w-4 sm:mr-2" />
            <span className="sr-only sm:not-sr-only">Sign out</span>
          </Button>
        </div>
      </div>
    </header>
  )
}
