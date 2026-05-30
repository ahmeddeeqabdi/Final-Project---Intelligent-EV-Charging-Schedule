/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        border: '#E0DDD5', // Muted off-white structural borders
        input: '#E0DDD5',
        ring: '#1A1A1A',
        background: '#F4F1EA', // Warm off-white
        foreground: '#1A1A1A', // Charcoal text
        primary: {
          DEFAULT: '#D95C14', // Burnt Orange
          foreground: '#F4F1EA',
        },
        secondary: {
          DEFAULT: '#EAE6DB',
          foreground: '#1A1A1A',
        },
        muted: {
          DEFAULT: '#E0DDD5',
          foreground: '#5C5C5C',
        },
        card: {
          DEFAULT: '#F7F5F0',
          foreground: '#1A1A1A',
        },
        warning: {
          DEFAULT: 'hsl(var(--warning))',
          foreground: 'hsl(var(--warning-foreground))',
        },
      },
      borderRadius: {
        lg: '0px',
        md: '0px',
        sm: '0px',
      },
      fontFamily: {
        sans: ['"IBM Plex Sans"', 'ui-sans-serif', 'system-ui', 'sans-serif'],
        display: ['"Space Grotesk"', 'ui-sans-serif', 'system-ui', 'sans-serif'],
        handwriting: ['"Caveat"', 'cursive'],
      },
      boxShadow: {
        soft: '0 18px 35px rgba(15, 73, 66, 0.12)',
        hard: '3px 3px 0px 0px rgba(26,26,26,1)',
        'hard-sm': '2px 2px 0px 0px rgba(26,26,26,1)',
      },
    },
  },
  plugins: [],
}

