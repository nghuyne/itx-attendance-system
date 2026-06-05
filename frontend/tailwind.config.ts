import type { Config } from 'tailwindcss'

export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        primary: '#059669',
        danger: '#dc2626',
        warning: '#f59e0b',
        success: '#22c55e',
        info: '#3b82f6',
        neutral: '#334155',
        background: '#f8fafc',
      },
    },
  },
  plugins: [],
} satisfies Config
