// Single source of truth for design tokens — avoid magic strings in components

export type ColorToken =
  | 'primary' | 'danger' | 'warning' | 'success' | 'info' | 'neutral' | 'background';

export type ToastType = 'success' | 'error' | 'info' | 'warning';
export type SpinnerSize = 'sm' | 'md' | 'lg';

// IMPORTANT: Must use full class strings — Tailwind JIT does not generate dynamic classes
// Wrong:  `border-${type}`          → JIT will NOT generate this class
// Right:  TOAST_COLORS[type].border → full string 'border-success' is detected by JIT
export const TOAST_COLORS: Record<ToastType, {
  border: string;
  bg: string;
  text: string;
  icon: string;
}> = {
  success: { border: 'border-success', bg: 'bg-green-50',  text: 'text-green-800',  icon: '✓' },
  error:   { border: 'border-danger',  bg: 'bg-red-50',    text: 'text-red-800',    icon: '✕' },
  info:    { border: 'border-info',    bg: 'bg-blue-50',   text: 'text-blue-800',   icon: 'ℹ' },
  warning: { border: 'border-warning', bg: 'bg-amber-50',  text: 'text-amber-800',  icon: '⚠' },
};

export const SPINNER_SIZES: Record<SpinnerSize, string> = {
  sm: 'w-4 h-4 border-2',
  md: 'w-8 h-8 border-4',
  lg: 'w-12 h-12 border-4',
};

// Typography convention (documentation only — not runtime code)
// H1:         text-2xl font-bold
// H2:         text-xl font-bold
// H3:         text-lg font-semibold
// Body:       text-base
// Caption:    text-sm / text-xs
// Monospace (timestamps, IP addresses): font-mono text-sm
