import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, test } from 'vitest';
import { VoiceNote } from './VoiceNote';

// The voice note player (tasks 4.5 and 4.7): a plain player, since wavesurfer.js isn't installed, used by keyboard
// alone. jsdom plays nothing (test/setup.ts), so these check the controls, the slider's values and the bars.

describe('VoiceNote', () => {
  test('Space and Enter play and pause; the slider moves by 5 s with the arrows and jumps with Home and End', async () => {
    const user = userEvent.setup();
    render(<VoiceNote src="blob:note" name="voice note" seconds={34} />);

    expect(screen.getByText('0:34')).toBeVisible();
    await user.tab();
    expect(screen.getByRole('button', { name: 'Play voice note' })).toHaveFocus();
    await user.keyboard(' ');
    expect(screen.getByRole('button', { name: 'Pause voice note' })).toBeVisible();
    await user.keyboard('{Enter}');
    expect(screen.getByRole('button', { name: 'Play voice note' })).toBeVisible();

    await user.tab();
    const slider = screen.getByRole('slider', { name: 'Position in voice note' });
    expect(slider).toHaveFocus();
    expect(slider).toHaveAttribute('aria-valuemax', '34');
    await user.keyboard('{ArrowRight}{ArrowRight}');
    expect(slider).toHaveAttribute('aria-valuenow', '10');
    expect(slider).toHaveAttribute('aria-valuetext', '0:10 of 0:34');
    await user.keyboard('{ArrowLeft}');
    expect(slider).toHaveAttribute('aria-valuenow', '5');
    await user.keyboard('{End}');
    expect(slider).toHaveAttribute('aria-valuenow', '34');
    await user.keyboard('{Home}');
    expect(slider).toHaveAttribute('aria-valuenow', '0');
  });

  test('before its length is known the slider says so and doesn’t move', async () => {
    const user = userEvent.setup();
    render(<VoiceNote src="https://storage.test/v.webm" name="voice note 2 of 2" />);

    const slider = screen.getByRole('slider', { name: 'Position in voice note 2 of 2' });
    expect(slider).toHaveAttribute('aria-disabled', 'true');
    expect(slider).toHaveAttribute('aria-valuetext', 'Length not known until it plays');
    slider.focus();
    await user.keyboard('{ArrowRight}');
    expect(slider).toHaveAttribute('aria-valuenow', '0');
  });

  test('draws 32 bars from a recording’s levels, and a plain line without them', () => {
    const levels = Array.from({ length: 32 }, (_, i) => i / 31);
    const { container, rerender } = render(
      <VoiceNote src="blob:note" name="voice note" seconds={3} levels={levels} />,
    );
    const slider = screen.getByRole('slider');
    expect(slider.querySelectorAll('span > span')).toHaveLength(32);

    rerender(<VoiceNote src="blob:note" name="voice note" seconds={3} />);
    expect(container.querySelectorAll('[role="slider"] span > span')).toHaveLength(1);
  });
});
