import { createContext } from 'react'
import { type AuthContextValue } from '@/types/api'

export const AuthContext = createContext<AuthContextValue | undefined>(undefined)
