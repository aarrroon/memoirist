import { act, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { afterEach, beforeAll, describe, expect, test, vi } from 'vitest';
import { useUiStore } from '../../stores/ui';
import { devUser } from '../../test/msw/handlers';
import { server } from '../../test/msw/server';
import { renderApp } from '../../test/render';

// The Write menu opens capture, which is React.lazy (task 4.6). Its first import transforms TipTap, Vaul and the
// rest, which took over 10 s on a cold run and failed the Write menu test; importing it here first means the lazy
// import finds it loaded, as CaptureView.test.tsx does.
beforeAll(async () => {
  await import('../../features/capture/CaptureView');
}, 60_000);

// The shell around every screen (task 4.3, DESIGN.md §4 Shell). jsdom applies no CSS, so both navs are in the
// tree: the laptop's side nav comes first in the document, the phone's tab bar last.
function navs() {
  const [sideNav, tabBar] = screen.getAllByRole('navigation', { name: 'Main' });
  if (!sideNav || !tabBar) throw new Error('expected the side nav and the tab bar');
  return { sideNav, tabBar };
}

function current(nav: HTMLElement): string[] {
  return within(nav)
    .queryAllByRole('link')
    .filter((link) => link.getAttribute('aria-current') === 'page')
    .map((link) => link.textContent ?? '');
}

afterEach(() => {
  useUiStore.setState({ capture: null });
  vi.useRealTimers();
});

describe('routes', () => {
  // Every route from the task, with the place each marks as current: [path, heading, tab bar, side nav, back link]
  test.each([
    ['/', 'Journal', 'Journal', 'Journal', null],
    ['/entries', 'Timeline', 'Timeline', 'Timeline', null],
    ['/weeks/3f2b1c9e-0000-4000-8000-000000000001', 'Week', 'Journal', 'Journal', 'Journal'],
    ['/eras', 'Eras', 'Eras', 'Eras', null],
    ['/templates', 'Templates', 'You', 'Templates', 'You'],
    ['/tags', 'Tags', 'You', 'You', 'You'],
    ['/settings', 'You', 'You', 'You', null],
    ['/recently-deleted', 'Recently deleted', 'You', 'Recently deleted', 'You'],
    ['/no/such/page', 'Not here', 'Journal', 'Journal', 'Journal'],
  ])(
    '%s shows %s, with exactly one place current in each nav',
    async (path, heading, tab, side, back) => {
      renderApp(path);

      expect(await screen.findByRole('heading', { level: 1, name: heading })).toBeInTheDocument();
      expect(document.title).toBe(`${heading} · memoirist`);
      const { sideNav, tabBar } = navs();
      expect(current(tabBar)).toEqual([tab]);
      expect(current(sideNav)).toEqual([side]);
      // An inner screen's top bar has a back link instead of the moon line; the side nav always has the moon.
      const topBar = screen.getByRole('banner');
      expect(
        within(topBar)
          .queryAllByRole('link')
          .map((link) => link.textContent),
      ).toEqual(back ? [back] : []);
      expect(await screen.findAllByRole('img', { name: /moon$/ })).toHaveLength(back ? 1 : 2);
    },
  );

  test('the tab bar has five columns: four places and Write in the centre', () => {
    renderApp('/');

    const items = [...navs().tabBar.children].map((item) => item.textContent);
    expect(items).toEqual(['Journal', 'Timeline', 'Write', 'Eras', 'You']);
  });

  test('a tab goes to its screen', async () => {
    const user = userEvent.setup();
    const { router } = renderApp('/');

    await user.click(within(navs().tabBar).getByRole('link', { name: 'Timeline' }));

    expect(router.state.location.pathname).toBe('/entries');
    expect(screen.getByRole('heading', { level: 1, name: 'Timeline' })).toBeInTheDocument();
    expect(current(navs().tabBar)).toEqual(['Timeline']);
  });

  test('the back link names where it goes, and goes there', async () => {
    const user = userEvent.setup();
    const { router } = renderApp('/recently-deleted');

    await user.click(within(screen.getByRole('banner')).getByRole('link', { name: 'You' }));

    expect(router.state.location.pathname).toBe('/settings');
  });

  test('You leads to Tags, Templates and Recently deleted, the only way there on a phone', async () => {
    const user = userEvent.setup();
    const { router } = renderApp('/settings');

    for (const [name, path] of [
      ['Tags', '/tags'],
      ['Templates', '/templates'],
      ['Recently deleted', '/recently-deleted'],
    ] as const) {
      await act(() => router.navigate('/settings'));
      await user.click(within(screen.getByRole('main')).getByRole('link', { name }));
      expect(router.state.location.pathname).toBe(path);
    }
  });
});

describe('the moon line', () => {
  test('greets the day in the user’s timezone, with the moon named', async () => {
    // Wednesday 25 November 2026 at 07:30 UTC: 18:30 in Melbourne (+11), dusk.
    vi.useFakeTimers({ toFake: ['Date'], now: new Date('2026-11-25T07:30:00Z') });
    renderApp('/');

    expect(await screen.findAllByText('Wednesday at dusk')).toHaveLength(2); // top bar and side nav
    expect(screen.getAllByRole('img', { name: /moon$/ })).toHaveLength(2);
  });

  test('follows the user’s timezone, not the machine’s', async () => {
    // The same instant is 02:30 on Wednesday in New York.
    vi.useFakeTimers({ toFake: ['Date'], now: new Date('2026-11-25T07:30:00Z') });
    server.use(
      http.get('*/api/v1/users/me', () =>
        HttpResponse.json({ ...devUser, timezone: 'America/New_York' }),
      ),
    );
    renderApp('/');

    expect(await screen.findAllByText('Wednesday morning')).toHaveLength(2);
  });

  test('stays empty until the timezone is known, rather than guess', () => {
    renderApp('/');

    expect(screen.queryAllByRole('img', { name: /moon/ })).toHaveLength(0);
    expect(screen.queryByText(/morning|afternoon|dusk|night/)).not.toBeInTheDocument();
  });
});

describe('the Write menu', () => {
  test('opens from the keyboard, chooses with Enter, and hands focus to capture', async () => {
    const user = userEvent.setup();
    renderApp('/');
    const write = within(navs().tabBar).getByRole('button', { name: 'Write' });

    write.focus();
    await user.keyboard('{Enter}');

    const menu = await screen.findByRole('menu');
    expect(
      within(menu)
        .getAllByRole('menuitem')
        .map((item) => item.textContent),
    ).toEqual(['New entry', 'Photo dump', 'Diary page']);
    await user.keyboard('{ArrowDown}{Enter}');

    expect(useUiStore.getState().capture).toBe('dump');
    await waitFor(() => expect(screen.queryByRole('menu')).not.toBeInTheDocument());
    // Task 4.6: the choice opens capture, which takes focus; the menu doesn't pull it back to Write behind the sheet.
    const sheet = await screen.findByRole('dialog', {}, { timeout: 10_000 });
    await waitFor(() => expect(sheet).toContainElement(document.activeElement as HTMLElement));
    expect(write).not.toHaveFocus();
  });

  test('Escape closes it without opening anything', async () => {
    const user = userEvent.setup();
    renderApp('/');
    const write = within(navs().tabBar).getByRole('button', { name: 'Write' });

    await user.click(write);
    expect(await screen.findByRole('menu')).toBeInTheDocument();
    await user.keyboard('{Escape}');

    await waitFor(() => expect(screen.queryByRole('menu')).not.toBeInTheDocument());
    expect(useUiStore.getState().capture).toBeNull();
    expect(write).toHaveFocus();
  });

  test('the laptop’s Write opens the same three choices', async () => {
    const user = userEvent.setup();
    renderApp('/eras');

    await user.click(
      within(screen.getByRole('complementary')).getByRole('button', { name: 'Write' }),
    );
    await user.click(await screen.findByRole('menuitem', { name: 'Diary page' }));

    expect(useUiStore.getState().capture).toBe('page');
  });
});

describe('keyboard', () => {
  test('the first Tab reaches Skip to content, which leads to the screen', async () => {
    const user = userEvent.setup();
    renderApp('/eras');

    await user.tab();
    const skip = screen.getByRole('link', { name: 'Skip to content' });
    expect(skip).toHaveFocus();
    await user.keyboard('{Enter}');
    expect(screen.getByRole('main')).toHaveFocus();
  });
});
