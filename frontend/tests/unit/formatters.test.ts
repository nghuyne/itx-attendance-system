import { describe, expect, it } from 'vitest';
import { formatDate } from '../../src/utils/formatters';

describe('formatDate', () => {
  it('returns an em dash for null/undefined', () => {
    expect(formatDate(null)).toBe('—');
    expect(formatDate(undefined)).toBe('—');
  });

  it('returns an em dash for an invalid date string', () => {
    expect(formatDate('not-a-date')).toBe('—');
  });

  it('formats a valid date as dd/mm/yyyy', () => {
    expect(formatDate('2026-07-17')).toBe('17/07/2026');
  });
});
