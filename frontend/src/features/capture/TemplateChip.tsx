import { Layers } from 'lucide-react';
import { useTemplates, type Template } from '../../api/templates';
import { Chip } from '../../components/ui/chip';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '../../components/ui/dropdown-menu';

// The template chip (DESIGN.md §4 Write, D-06): a menu of the user's templates. Choosing one inserts its prompts above
// what is already written (G-07); the caller does that, since it owns the editor. Templates are made on the Templates
// screen (task 4.9), so with none there is one disabled row saying so.
export function TemplateChip({
  chosen,
  onChoose,
}: {
  chosen: string | null;
  onChoose: (template: Template) => void;
}) {
  const templates = useTemplates();
  const list = templates.data ?? [];
  const name = list.find((t) => t.id === chosen)?.name;

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Chip aria-label={name ? `Template: ${name}. Change it` : 'Choose a template'}>
          <Layers size={16} aria-hidden />
          {name ?? 'Template'}
        </Chip>
      </DropdownMenuTrigger>
      {/* z-50: above the capture sheet or dialog (z-40), which the shell's own menus never meet. */}
      <DropdownMenuContent align="start" className="z-50">
        {list.length === 0 ? (
          <DropdownMenuItem disabled className="text-ink-2">
            {templates.isPending ? 'Loading templates…' : 'No templates yet'}
          </DropdownMenuItem>
        ) : (
          list.map((template) => (
            <DropdownMenuItem key={template.id} onSelect={() => onChoose(template)}>
              {template.name}
            </DropdownMenuItem>
          ))
        )}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
