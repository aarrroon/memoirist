// The starter page (task 4.1). It shows the tokens working in light and dark (D-40), and nothing else:
// task 4.3 replaces it with the router and the app shell, built from docs/design/DESIGN.md (D-41).
export default function App() {
  return (
    <main className="flex flex-col gap-2 px-4 py-8 md:px-6 lg:px-8">
      <h1 className="font-display text-heading">memoirist</h1>
      <p className="text-body text-ink-2">Your journal is on its way.</p>
    </main>
  );
}
