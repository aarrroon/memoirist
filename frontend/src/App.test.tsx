import { render, screen } from '@testing-library/react';
import { expect, test } from 'vitest';
import App from './App';

test('the starter page names the app', () => {
  render(<App />);

  expect(screen.getByRole('heading', { level: 1, name: 'memoirist' })).toBeInTheDocument();
});
