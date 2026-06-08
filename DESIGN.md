# ACRQG Console Design System

This frontend follows an agent-readable admin-console design system inspired by the `awesome-design-md` approach: explicit tokens, reusable layout rules, and consistent dashboard patterns.

## Design direction

**Name:** Aegis Console  
**Mood:** modern developer/security operations dashboard  
**Principles:** dense but calm, high contrast navigation, clean data surfaces, obvious primary actions, minimal decoration.

## Visual tokens

- App background: soft blue-gray canvas with subtle radial accents.
- Sidebar: dark navy technical surface with cyan/blue active states.
- Cards: white elevated panels, 18px radius, low shadow, 1px translucent border.
- Primary accent: blue/cyan gradient for active navigation and CTAs.
- Semantic colors: success green, warning amber, danger red, info blue.
- Typography: system sans for UI; JetBrains/Consolas-style monospace for commit IDs and code.
- Density: tables remain information-rich, but use better row height, hover states, sticky-feeling panel rhythm.

## Layout rules

1. Use a persistent left sidebar for primary navigation.
2. Use a top utility bar for project switching, notification status, and user/session controls.
3. Every route renders inside a content canvas with consistent max width and spacing.
4. Prefer card panels for filters, forms, tables, charts, and reports.
5. Keep page actions right-aligned and make the main CTA visually obvious.
6. Preserve data density: do not hide table columns or existing actions during redesign.

## Component rules

### Navigation
- Group menu items by product area: Overview, Delivery, Administration.
- Active item uses gradient border/accent and stronger text.
- Keep role-based visibility exactly as implemented in route/store logic.

### Cards and panels
- Use `shadow="never"`; visual depth comes from global card tokens.
- Use consistent padding and rounded corners.
- Filter cards should look like toolbars: compact, aligned, wrapping when needed.

### Tables
- Zebra striping is allowed, but hover state must be stronger than zebra state.
- Header should be uppercase-ish/semibold and visually separated.
- Row click affordance should remain for list/detail pages.

### Forms
- Use top labels for admin forms.
- Inputs should have soft backgrounds, stronger focus ring, and consistent radius.
- Validation text remains Element Plus default semantics.

### Charts
- Use restrained palette and grid lines.
- Cards containing charts should include clear section titles in page templates when feasible.

## Implementation guardrails

- Keep Vue routes, API calls, Pinia stores, and existing permissions unchanged unless a functional bug is being fixed.
- Centralize broad styling in `src/styles/index.scss`.
- Avoid adding heavy UI dependencies; build on Element Plus and existing ECharts.
- Do not overuse gradients; reserve gradients for active navigation, login hero, and major CTA affordances.
- New pages should follow the shell/card/table/form rhythm documented here.
