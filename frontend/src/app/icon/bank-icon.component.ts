import { Component, Input } from '@angular/core';
@Component({ selector: 'nb-icon', standalone: true, template: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path [attr.d]="paths[name] || paths['arrow']"/></svg>` })
export class BankIconComponent {
    @Input()
    name = 'arrow';
    readonly paths: Record<string, string> = {
        overview: 'M3 3h7v7H3z M14 3h7v7h-7z M3 14h7v7H3z M14 14h7v7h-7z',
        transactions: 'M4 7h16 M16 3l4 4-4 4 M20 17H4 M8 13l-4 4 4 4',
        history: 'M4 6V2 M4 6h4 M4 6a9 9 0 1 1-1 10 M12 7v5l3 2',
        shield: 'M12 3l8 3v6c0 5-8 9-8 9s-8-4-8-9V6z M8 12l3 3 5-6',
        arrow: 'M5 12h14 M13 6l6 6-6 6',
        chevron: 'M9 5l7 7-7 7',
        search: 'M21 21l-5-5 M18 10a8 8 0 1 1-16 0 8 8 0 0 1 16 0',
        close: 'M6 6l12 12 M6 18L18 6',
        logout: 'M9 4H4v16h5 M10 12h11 M16 7l5 5-5 5',
        wallet: 'M3 6h16v14H3z M3 6V4h14v2 M15 11h6v5h-6z M17 13.5h.01',
        check: 'M5 12l4 4L19 6',
        clock: 'M12 3a9 9 0 1 1 0 18 9 9 0 0 1 0-18 M12 7v5l3 2',
        info: 'M12 3a9 9 0 1 1 0 18 9 9 0 0 1 0-18 M12 11v6 M12 7h.01',
        lock: 'M6 10h12v11H6z M8 10V6a4 4 0 0 1 8 0v4 M12 14v3',
        help: 'M12 3a9 9 0 1 1 0 18 9 9 0 0 1 0-18 M9 9a3 3 0 0 1 6 0c0 2-3 2-3 5 M12 17h.01',
        download: 'M12 3v12 M7 10l5 5 5-5 M4 16v5h16v-5',
        card: 'M3 5h18v14H3z M3 9h18 M6 15h4',
        mail: 'M3 5h18v14H3z M3 5l9 8 9-8',
        refresh: 'M20 7V3 M20 7h-4 M20 7a9 9 0 1 0 1 9',
        menu: 'M4 6h16 M4 12h16 M4 18h16'
    };
}
