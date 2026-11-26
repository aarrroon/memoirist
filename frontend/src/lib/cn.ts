import { clsx, type ClassValue } from 'clsx';
import { extendTailwindMerge } from 'tailwind-merge';

// Joins class names and lets a later class win over an earlier one of the same kind, so a component's caller can
// override one of its classes: cn('px-3', 'px-4') is 'px-4' (the shadcn/ui helper, D-13).
//
// tailwind-merge only knows Tailwind's own scale names. Our text sizes (D-40) aren't T-shirt sizes, so without
// this it would take `text-body` for a colour, and cn('text-body', 'text-ink-2') would silently drop the size.
// The same goes for the `strong` weight and the `float` shadow. Keep these lists in step with src/index.css.
const twMerge = extendTailwindMerge({
  extend: {
    theme: {
      text: ['meta', 'small', 'body', 'read', 'title', 'heading', 'display'],
      'font-weight': ['strong'],
      shadow: ['float'],
    },
  },
});

export function cn(...inputs: ClassValue[]): string {
  return twMerge(clsx(inputs));
}
