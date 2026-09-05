# Presentation visual system

## Selected system

The generated deck uses Lucide icons as its reproducible diagram asset library. The icons share one stroke language and can be recoloured with the Customer Activity Analytics deck palette. The build renders only the selected assets and embeds them into the PowerPoint file. The Lucide and inherited Feather notices are retained in `assets/licenses/LUCIDE-LICENSE.txt`.

Diagram structure remains native PowerPoint geometry so labels, containers and connectors remain directly editable. Icons clarify the meaning of a node; they do not replace evidence, charts or labels.

## Asset and tool routing

| Need | Preferred source | Delivery format |
| --- | --- | --- |
| Common concepts in the generated deck | [Lucide](https://lucide.dev/) | Selected icon embedded in PowerPoint |
| Manual final polish in Microsoft 365 | [PowerPoint Icons](https://support.microsoft.com/en-us/powerpoint/insert-icons-in-microsoft-365) | Native Office icon or converted SVG shape |
| Microsoft-aligned iconography | [Fluent UI System Icons](https://github.com/microsoft/fluentui-system-icons) | Plain SVG with its licence retained |
| Complex routing, connection points or reusable diagram fragments | [draw.io](https://www.drawio.com/docs/manual/shapes/) | Keep the `.drawio` source and export SVG for PowerPoint |
| Actual cloud infrastructure | Official provider icon pack | Use only icons for deployed provider services |

Draw.io is useful when a diagram needs automatic layout, specialised connection points or a reusable corporate shape library. Its source file should remain next to the deck. An SVG export is the presentation view, not the editable authority.

## Diagram grammar

- Use one icon family on a slide and one icon per semantic node.
- Use icons as identifiers, never as decoration.
- Keep the primary reading direction left to right.
- Attach connectors to object boundaries and use orthogonal routes where possible.
- Use red for decisions or control, blue for context or delivery, green for accepted evidence and amber for conditional or cautionary content.
- Do not use AWS, Azure or other provider icons unless the diagram represents those actual services.
- Keep distributions and measured relationships as plots rather than converting them into icon sequences.
