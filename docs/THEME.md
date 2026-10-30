# Interface colour theme

The Ntsumele interface uses the colour direction of [Capitec's public website](https://www.capitecbank.co.za/), inspected on 29 October 2026. Colours were read from the rendered public page. The red was confirmed in its [public logo SVG](https://www.capitecbank.co.za/Static/capitec_v2/dist/assets/images/capitec-logo-small.svg). This is a colour reference; the application retains its Ntsumele name, N monogram, wording, layout and typeface.

| Role | Colour | Application |
| --- | --- | --- |
| Website primary blue | `#2F70EF` | Brand token, focus rings, highlights and browser theme colour |
| Website heading blue | `#0033A0` | Major headings, links, login panel and selected financial highlights |
| Public logo red | `#E63934` | Ntsumele monogram and accent details |
| Charcoal | `#3A3A3A` | Main text |
| White / light grey | `#FFFFFF` / `#F7F7F7` | Navigation, cards, forms and page surfaces |
| Accessible action blue | `#2B6BE8` | Small primary buttons and selected navigation with white text |

The action-blue variant is slightly darker than the sampled website button blue so white text meets 4.5:1 normal-text contrast. Blue, red, neutral, success and pending colours are centralized as CSS custom properties at the top of `frontend/src/styles.css`.

The theme covers sign-in, sidebar navigation, account overview, transaction activity, dispute history, case dialogs, inputs, focus outlines, notifications and mobile layouts. Green and amber remain reserved for meaningful banking states such as resolved/completed cases, credits and pending/information-requested cases; the old green branding has been replaced.

Checked foreground/background combinations:

| Use | Contrast |
| --- | --- |
| White text on action-blue buttons/navigation | 4.80:1 |
| Main text on page background | 10.62:1 |
| Secondary text on page background | 5.78:1 |
| Deep-blue labels on blue-tinted surfaces | 9.54:1 |
| Red labels on red-tinted surfaces | 5.73:1 |
| Success labels on success-tinted surfaces | 6.38:1 |
| Pending labels on warning-tinted surfaces | 6.32:1 |

These are calculated checks of the listed pairs, not a full accessibility audit. Existing keyboard focus, status text, disabled controls and responsive breakpoints are preserved. The Angular tests and production compilation pass. The local preview server could not be reached through the Windows sandbox's loopback restrictions, so the updated application has not been visually checked in a browser here.

Rebuild the Docker image from the project directory to see the theme in a running container:

```sh
docker compose up -d --build
```

The PostgreSQL named volume retains demo data across application/container recreation. Keep that volume and follow the backup procedure in [OPERATIONS.md](OPERATIONS.md); deleting volumes removes the database.
