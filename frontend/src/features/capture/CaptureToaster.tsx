import { Toaster } from 'sonner';

// Sonner's toaster (D-13) for capture's "Saved in the week of …, Go there" (G-04, task 4.6). `unstyled` drops Sonner's
// own colours, and each part gets token classes instead (design.md, Tokens): `--page`, the one shadow and `lg`
// corners, like the other floating things. It sits above the tab bar on a phone.
export default function CaptureToaster() {
  return (
    <Toaster
      position="bottom-center"
      offset={{ bottom: 80 }}
      mobileOffset={{ bottom: 80 }}
      toastOptions={{
        unstyled: true,
        classNames: {
          toast:
            'flex w-full items-center gap-4 rounded-lg border border-line bg-page px-4 py-3 text-body text-ink shadow-float',
          title: 'flex-1',
          actionButton:
            'min-h-11 shrink-0 rounded-full border border-control px-4 text-body font-strong text-ink',
        },
      }}
    />
  );
}
