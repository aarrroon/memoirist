import { afterEach, describe, expect, test, vi } from 'vitest';
import {
  countdown,
  dayLabel,
  dayParts,
  formatDay,
  formatMoment,
  formatOpens,
  formatWeekRange,
  greeting,
  isWithin,
  partOfDay,
  todayIn,
  chipDay,
  formatClock,
  fromPickerDate,
  toPickerDate,
  weekOf,
  addDays,
  daysBetween,
  dayIn,
  latestSealDay,
  openedLabel,
  openingParts,
  sealProgress,
  unlockInstant,
} from './time';

// Plain unit tests with fixed instants (frontend.md: plain Vitest for lib/). Every instant is written in UTC, so
// what each test expects can be worked out by hand from the zone's offset at that moment.
const MELBOURNE = 'Australia/Melbourne';
const NEW_YORK = 'America/New_York';
const KOLKATA = 'Asia/Kolkata'; // +05:30 all year: a half-hour offset catches code that only shifts whole hours
const utc = (iso: string) => new Date(iso);

describe('Melbourne, across the daylight-saving change in October 2026', () => {
  // Clocks go forward at 02:00 AEST (+10:00) on Sunday 4 October 2026, to 03:00 AEDT (+11:00).
  test('just after midnight on the change day it is already the 4th, in standard time', () => {
    // 14:30 UTC on the 3rd is 00:30 AEST on the 4th.
    expect(todayIn(MELBOURNE, utc('2026-10-03T14:30:00Z'))).toBe('2026-10-04');
  });

  test('the next midnight comes an hour earlier in UTC, because the offset is now +11', () => {
    // 13:30 UTC on the 4th is 00:30 AEDT on the 5th. A fixed +10 offset would still say 23:30 on the 4th.
    expect(todayIn(MELBOURNE, utc('2026-10-04T13:30:00Z'))).toBe('2026-10-05');
  });

  test('the part of the day follows the new offset', () => {
    // 06:30 UTC on the 4th is 17:30 AEDT: dusk. At +10 it would have been 16:30, the afternoon.
    expect(partOfDay(utc('2026-10-04T06:30:00Z'), MELBOURNE)).toBe('at dusk');
    expect(greeting(utc('2026-10-04T06:30:00Z'), MELBOURNE)).toBe('Sunday at dusk');
  });

  test('an opening moment at 9:00 am is written as 9:00 am on both sides of the change', () => {
    // 9:00 AEST on 3 October is 23:00 UTC on the 2nd; 9:00 AEDT on 5 October is 22:00 UTC on the 4th.
    expect(formatMoment('2026-10-02T23:00:00Z', MELBOURNE)).toBe(
      'Saturday 3 October 2026 at 9:00 am',
    );
    expect(formatMoment('2026-10-04T22:00:00Z', MELBOURNE)).toBe(
      'Monday 5 October 2026 at 9:00 am',
    );
  });
});

describe('Melbourne, when daylight saving ends in April 2026', () => {
  // Clocks go back at 03:00 AEDT (+11:00) on Sunday 5 April 2026, to 02:00 AEST (+10:00).
  test('midnight before the change is at +11, midnight after it at +10', () => {
    expect(todayIn(MELBOURNE, utc('2026-04-04T13:30:00Z'))).toBe('2026-04-05'); // 00:30 AEDT on the 5th
    expect(todayIn(MELBOURNE, utc('2026-04-05T13:30:00Z'))).toBe('2026-04-05'); // 23:30 AEST, still the 5th
    expect(todayIn(MELBOURNE, utc('2026-04-05T14:30:00Z'))).toBe('2026-04-06'); // 00:30 AEST on the 6th
  });

  test('the repeated hour is the same morning both times', () => {
    // 02:30 happens twice: 15:30 UTC (AEDT) and 16:30 UTC (AEST) on the 4th.
    expect(partOfDay(utc('2026-04-04T15:30:00Z'), MELBOURNE)).toBe('morning');
    expect(partOfDay(utc('2026-04-04T16:30:00Z'), MELBOURNE)).toBe('morning');
  });
});

describe('zones other than Melbourne', () => {
  // One instant, three different days: 23:30 UTC on 31 December 2026.
  const newYearsEve = utc('2026-12-31T23:30:00Z');

  test('the same instant is a different day in each zone', () => {
    expect(todayIn(MELBOURNE, newYearsEve)).toBe('2027-01-01'); // 10:30 AEDT
    expect(todayIn(NEW_YORK, newYearsEve)).toBe('2026-12-31'); // 18:30 EST
    expect(todayIn(KOLKATA, newYearsEve)).toBe('2027-01-01'); // 05:00 IST
    expect(todayIn('UTC', newYearsEve)).toBe('2026-12-31');
  });

  test('the greeting uses the half-hour offset', () => {
    // 06:29 UTC is 11:59 IST (morning); 06:30 UTC is 12:00 IST (afternoon).
    expect(greeting(utc('2026-11-25T06:29:00Z'), KOLKATA)).toBe('Wednesday morning');
    expect(greeting(utc('2026-11-25T06:30:00Z'), KOLKATA)).toBe('Wednesday afternoon');
  });

  test('New York changes its clocks on its own dates', () => {
    // US daylight saving ends at 02:00 EDT on Sunday 1 November 2026: 05:30 UTC is 01:30 EDT, 06:30 UTC is 01:30 EST.
    expect(formatMoment('2026-11-01T05:30:00Z', NEW_YORK)).toBe(
      'Sunday 1 November 2026 at 1:30 am',
    );
    expect(formatMoment('2026-11-01T06:30:00Z', NEW_YORK)).toBe(
      'Sunday 1 November 2026 at 1:30 am',
    );
    // The same two instants are an hour apart in Melbourne, which doesn't change that day.
    expect(formatMoment('2026-11-01T05:30:00Z', MELBOURNE)).toBe(
      'Sunday 1 November 2026 at 4:30 pm',
    );
    expect(formatMoment('2026-11-01T06:30:00Z', MELBOURNE)).toBe(
      'Sunday 1 November 2026 at 5:30 pm',
    );
  });
});

// vi.stubEnv sets process.env.TZ, and Node switches the machine's zone as soon as TZ is assigned. Plain Date
// getters follow it; time.ts must not.
afterEach(() => {
  vi.unstubAllEnvs();
});

describe('the browser’s own zone is never used', () => {
  test('changing the machine’s zone changes nothing', () => {
    const instant = utc('2026-10-04T13:30:00Z');
    vi.stubEnv('TZ', 'America/Los_Angeles'); // 06:30 PDT on the 4th
    expect(new Date(instant).getDate()).toBe(4); // the machine now thinks it is the 4th...
    expect(todayIn(MELBOURNE, instant)).toBe('2026-10-05'); // ...but the user's today is the 5th
    expect(partOfDay(instant, MELBOURNE)).toBe('morning');
    vi.stubEnv('TZ', 'Pacific/Kiritimati'); // +14
    expect(todayIn(MELBOURNE, instant)).toBe('2026-10-05');
    expect(partOfDay(instant, MELBOURNE)).toBe('morning');
  });
});

describe('the parts of the day (DESIGN.md §4)', () => {
  // Melbourne is +11 on 25 November 2026, so 13:00 UTC on the 24th is midnight.
  test.each([
    ['2026-11-24T13:00:00Z', 'morning'], // 00:00
    ['2026-11-25T00:59:00Z', 'morning'], // 11:59
    ['2026-11-25T01:00:00Z', 'afternoon'], // 12:00
    ['2026-11-25T05:59:00Z', 'afternoon'], // 16:59
    ['2026-11-25T06:00:00Z', 'at dusk'], // 17:00
    ['2026-11-25T09:59:00Z', 'at dusk'], // 20:59
    ['2026-11-25T10:00:00Z', 'at night'], // 21:00
    ['2026-11-25T12:59:00Z', 'at night'], // 23:59
  ])('%s is %s', (instant, part) => {
    expect(partOfDay(utc(instant), MELBOURNE)).toBe(part);
  });
});

describe('calendar days', () => {
  test('a day is written the same in every zone, because it has none', () => {
    vi.stubEnv('TZ', 'America/Los_Angeles');
    expect(formatDay('2026-10-26')).toBe('Monday 26 October');
    expect(formatDay('2026-10-26', { year: true })).toBe('Monday 26 October 2026');
    vi.stubEnv('TZ', 'Pacific/Kiritimati');
    expect(formatDay('2026-10-26')).toBe('Monday 26 October');
  });

  test('a malformed day is refused, not guessed', () => {
    expect(() => formatDay('26/10/2026')).toThrow(RangeError);
  });

  test('today and yesterday are named in the user’s zone', () => {
    const now = utc('2026-10-04T13:30:00Z'); // 00:30 on the 5th in Melbourne, 09:30 on the 4th in New York
    expect(dayLabel('2026-10-05', MELBOURNE, now)).toBe('Today');
    expect(dayLabel('2026-10-04', MELBOURNE, now)).toBe('Yesterday');
    expect(dayLabel('2026-10-03', MELBOURNE, now)).toBe('Saturday 3 October');
    expect(dayLabel('2026-10-04', NEW_YORK, now)).toBe('Today');
    expect(dayLabel('2026-10-05', NEW_YORK, now)).toBe('Monday 5 October');
  });

  test('yesterday crosses a month and a year', () => {
    expect(dayLabel('2026-12-31', MELBOURNE, utc('2026-12-31T23:30:00Z'))).toBe('Yesterday');
  });
});

describe('the feed’s weeks and sealed cards (task 4.4)', () => {
  test('a week names its dates as briefly as it can', () => {
    expect(formatWeekRange('2026-11-16', '2026-11-22')).toBe('16–22 November');
    expect(formatWeekRange('2026-11-30', '2026-12-06')).toBe('30 November – 6 December');
    expect(formatWeekRange('2026-12-28', '2027-01-03')).toBe('28 December 2026 – 3 January 2027');
  });

  test('a day is inside a week from its Monday to its Sunday, both included', () => {
    expect(isWithin('2026-11-23', '2026-11-23', '2026-11-29')).toBe(true);
    expect(isWithin('2026-11-29', '2026-11-23', '2026-11-29')).toBe(true);
    expect(isWithin('2026-11-30', '2026-11-23', '2026-11-29')).toBe(false);
    expect(isWithin('2026-11-22', '2026-11-23', '2026-11-29')).toBe(false);
  });

  test('the laptop margin splits a day into its weekday and a short date', () => {
    expect(dayParts('2026-10-26')).toEqual({ weekday: 'Monday', short: '26 Oct' });
  });

  test('the opening day is the day in the user’s zone, not in UTC', () => {
    // 9:00 am AEDT on Monday 25 January 2027 is 22:00 UTC on Sunday the 24th.
    expect(formatOpens('2027-01-24T22:00:00Z', MELBOURNE)).toBe('Mon 25 Jan 2027');
    expect(formatOpens('2027-01-24T22:00:00Z', NEW_YORK)).toBe('Sun 24 Jan 2027');
  });

  test('the countdown counts calendar days in the user’s zone', () => {
    const opens = '2027-01-24T22:00:00Z'; // 9:00 am on 25 January in Melbourne
    // 23:00 AEDT on 24 January: the opening is tomorrow morning, ten hours away.
    expect(countdown(opens, MELBOURNE, utc('2027-01-24T12:00:00Z'))).toBe('tomorrow');
    // 08:00 AEDT on the day itself: still sealed for an hour.
    expect(countdown(opens, MELBOURNE, utc('2027-01-24T21:00:00Z'))).toBe('today');
    // 23:47 AEDT on Tuesday 24 November 2026.
    expect(countdown(opens, MELBOURNE, utc('2026-11-24T12:47:00Z'))).toBe('in 62 days');
  });

  test('a countdown across the April daylight-saving change is not one day off', () => {
    // 9:00 am AEST on Tuesday 7 April 2026 is 23:00 UTC on the 6th. Clocks went back on Sunday 5 April.
    expect(countdown('2026-04-06T23:00:00Z', MELBOURNE, utc('2026-04-03T01:00:00Z'))).toBe(
      'in 4 days',
    );
  });
});

describe('capture (task 4.6)', () => {
  test('weekOf finds the Monday-to-Sunday week, across a month and a year', () => {
    expect(weekOf('2026-11-24')).toEqual({ start: '2026-11-23', end: '2026-11-29' });
    expect(weekOf('2026-11-23')).toEqual({ start: '2026-11-23', end: '2026-11-29' });
    expect(weekOf('2026-11-29')).toEqual({ start: '2026-11-23', end: '2026-11-29' });
    expect(weekOf('2027-01-01')).toEqual({ start: '2026-12-28', end: '2027-01-03' });
  });

  test('the date chip says Today in the user’s zone, and the year only for another year', () => {
    // 23:30 on Monday 23 November in New York is already Tuesday the 24th in Melbourne.
    const now = utc('2026-11-24T04:30:00Z');
    expect(chipDay('2026-11-24', MELBOURNE, now)).toBe('Today, Tue 24 Nov');
    expect(chipDay('2026-11-24', NEW_YORK, now)).toBe('Tue 24 Nov');
    expect(chipDay('2026-11-23', NEW_YORK, now)).toBe('Today, Mon 23 Nov');
    expect(chipDay('2003-03-14', MELBOURNE, now)).toBe('Fri 14 Mar 2003');
  });

  test('"Draft saved" gives the time in the user’s zone, across Melbourne’s change to daylight time', () => {
    expect(formatClock('2026-10-03T15:30:00Z', MELBOURNE)).toBe('1:30 am');
    expect(formatClock('2026-10-03T16:30:00Z', MELBOURNE)).toBe('3:30 am');
    expect(formatClock('2026-11-24T08:41:00Z', KOLKATA)).toBe('2:11 pm');
  });

  test('the picker’s Date round-trips to the same calendar day, back to 1900', () => {
    for (const day of ['2026-11-24', '1900-01-01', '2026-10-04', '2024-02-29']) {
      expect(fromPickerDate(toPickerDate(day))).toBe(day);
    }
  });
});

describe('Loot Drops (task 4.8, D-23)', () => {
  test('a Loot Drop opens at 9:00 am on its day in the user’s zone, in both of Melbourne’s seasons', () => {
    // October is daylight time (+11), June standard time (+10): one fixed offset gets one of them wrong.
    expect(unlockInstant('2027-10-28', MELBOURNE)).toBe('2027-10-27T22:00:00.000Z');
    expect(unlockInstant('2027-06-28', MELBOURNE)).toBe('2027-06-27T23:00:00.000Z');
  });

  test('on the day Melbourne’s clocks go forward, 9:00 am is already daylight time', () => {
    // 4 October 2026: 2:00 am became 3:00 am, so 9:00 am that day is 22:00 UTC the day before.
    expect(unlockInstant('2026-10-04', MELBOURNE)).toBe('2026-10-03T22:00:00.000Z');
  });

  test('another zone opens at its own 9:00 am, not Melbourne’s and not the browser’s', () => {
    expect(unlockInstant('2027-07-01', 'Europe/London')).toBe('2027-07-01T08:00:00.000Z');
    expect(unlockInstant('2027-07-01', KOLKATA)).toBe('2027-07-01T03:30:00.000Z');
    expect(unlockInstant('2027-01-15', NEW_YORK)).toBe('2027-01-15T14:00:00.000Z');
  });

  test('the opening day and moment are read back in the user’s zone', () => {
    const opens = '2027-10-24T22:00:00Z'; // 9:00 am on Monday 25 October 2027 in Melbourne
    expect(dayIn(opens, MELBOURNE)).toBe('2027-10-25');
    expect(dayIn(opens, NEW_YORK)).toBe('2027-10-24');
    expect(openingParts(opens, MELBOURNE)).toEqual({
      day: 'Monday 25 October 2027',
      time: '9:00 am',
    });
  });

  test('calendar arithmetic crosses months, leap days and daylight-saving changes', () => {
    expect(addDays('2026-11-30', 1)).toBe('2026-12-01');
    expect(addDays('2028-02-28', 1)).toBe('2028-02-29');
    expect(addDays('2026-10-05', -2)).toBe('2026-10-03');
    expect(daysBetween('2026-10-25', '2027-10-25')).toBe(365);
    expect(daysBetween('2026-04-04', '2026-04-06')).toBe(2);
  });

  test('a seal can open at most the day before its 100th anniversary', () => {
    expect(latestSealDay(MELBOURNE, utc('2026-11-24T12:47:00Z'))).toBe('2126-11-23');
    // 23:30 on 24 November in New York: Melbourne's today is the 25th.
    expect(latestSealDay(MELBOURNE, utc('2026-11-25T04:30:00Z'))).toBe('2126-11-24');
  });

  test('the star’s lit part is the days travelled over the days of the whole wait', () => {
    // Written Sunday 25 October 2026, opens 9:00 am Monday 25 October 2027 in Melbourne: 365 days.
    const opens = '2027-10-24T22:00:00Z';
    expect(sealProgress('2026-10-25', opens, MELBOURNE, utc('2026-10-28T01:00:00Z'))).toEqual({
      travelled: 3,
      total: 365,
      fraction: 3 / 365,
    });
    // At 1 am on 28 October in Melbourne it is still the 27th in UTC: the zone decides, not UTC.
    expect(
      sealProgress('2026-10-25', opens, MELBOURNE, utc('2026-10-27T14:00:00Z')).travelled,
    ).toBe(3);
    // Before the memory date (a Loot Drop dated ahead is impossible, but the clock may be off) and after opening.
    expect(sealProgress('2026-10-25', opens, MELBOURNE, utc('2026-10-01T00:00:00Z')).fraction).toBe(
      0,
    );
    expect(sealProgress('2026-10-25', opens, MELBOURNE, utc('2028-01-01T00:00:00Z')).fraction).toBe(
      1,
    );
    // Sealed today to open tomorrow: 0 of 1, then 1 of 1.
    expect(
      sealProgress('2026-11-24', '2026-11-24T22:00:00Z', MELBOURNE, utc('2026-11-24T05:00:00Z')),
    ).toEqual({
      travelled: 0,
      total: 1,
      fraction: 0,
    });
  });

  test('"Opened" names today, yesterday or the day in the user’s zone', () => {
    const now = utc('2026-11-24T12:47:00Z'); // 11:47 pm on Tuesday 24 November in Melbourne
    expect(openedLabel('2026-11-23T22:00:00Z', MELBOURNE, now)).toBe('Opened today');
    expect(openedLabel('2026-11-22T22:00:00Z', MELBOURNE, now)).toBe('Opened yesterday');
    expect(openedLabel('2026-11-02T22:00:00Z', MELBOURNE, now)).toBe('Opened 3 Nov 2026');
  });
});
