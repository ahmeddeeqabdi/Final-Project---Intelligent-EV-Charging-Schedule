import * as React from 'react'
import { cva, type VariantProps } from 'class-variance-authority'
import { Slot } from '@radix-ui/react-slot'
import { cn } from '@/lib/utils'

const buttonVariants = cva(
  'inline-flex min-h-11 items-center justify-center rounded-none text-sm font-semibold transition-all focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-foreground focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-60 ring-offset-background px-4',
  {
    variants: {
      variant: {
        default: 'bg-primary text-primary-foreground border border-foreground shadow-[2px_2px_0px_#1A1A1A] hover:bg-primary/90 hover:translate-y-[1px] hover:translate-x-[1px] hover:shadow-[1px_1px_0px_#1A1A1A] active:translate-y-[2px] active:translate-x-[2px] active:shadow-none',
        secondary: 'bg-card text-foreground border border-border shadow-[2px_2px_0px_#E0DDD5] hover:bg-border hover:translate-y-[1px] hover:translate-x-[1px] hover:shadow-[1px_1px_0px_#E0DDD5] active:translate-y-[2px] active:translate-x-[2px] active:shadow-none',
        ghost: 'bg-transparent text-foreground hover:bg-[#E0DDD5]',
      },
      size: {
        default: 'h-11',
        lg: 'h-12 px-5 text-base',
      },
    },
    defaultVariants: {
      variant: 'default',
      size: 'default',
    },
  },
)

export interface ButtonProps
  extends React.ButtonHTMLAttributes<HTMLButtonElement>,
    VariantProps<typeof buttonVariants> {
  asChild?: boolean
}

const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant, size, asChild = false, ...props }, ref) => {
    const Comp = asChild ? Slot : 'button'
    return <Comp className={cn(buttonVariants({ variant, size, className }))} ref={ref} {...props} />
  },
)

Button.displayName = 'Button'

export { Button }
