import { useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { AuthLayout, FormError, PasswordField } from '@/components/auth/AuthLayout'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Spinner } from '@/components/ui/spinner'
import { useAuth } from '@/hooks/useAuth'
import { ApiError } from '@/services/apiClient'

export function LoginPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const { login } = useAuth()

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  const from = (location.state as { from?: string } | null)?.from ?? '/'

  const onSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setIsSubmitting(true)
    setError(null)

    try {
      await login({ email, password })
      navigate(from, { replace: true })
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'Unable to sign in right now. Please try again.')
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <AuthLayout title="Welcome back" description="Sign in to plan your next charge.">
      <form className="space-y-5" onSubmit={onSubmit}>
        <div className="space-y-1.5">
          <Label htmlFor="email">Email</Label>
          <Input id="email" type="email" value={email} onChange={(event) => setEmail(event.target.value)} autoComplete="email" placeholder="you@example.com" required autoFocus />
        </div>

        <PasswordField id="password" label="Password" value={password} onChange={setPassword} autoComplete="current-password" />

        <FormError message={error} />

        <Button type="submit" size="lg" className="w-full" disabled={isSubmitting}>
          {isSubmitting ? <span className="inline-flex items-center gap-2"><Spinner className="h-4 w-4 text-primary-foreground" />Signing in…</span> : 'Sign in'}
        </Button>

        <p className="text-center text-sm text-muted-foreground">
          New here?{' '}
          <Link to="/signup" className="font-semibold text-primary hover:underline">Create an account</Link>
        </p>
      </form>
    </AuthLayout>
  )
}
