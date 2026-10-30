import { Component, input, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { BankIconComponent } from '../icon/bank-icon.component';
import { SessionSignInRequest } from './session-identity';
@Component({ selector: 'nb-bank-sign-in', standalone: true, imports: [FormsModule, BankIconComponent], templateUrl: './bank-sign-in.component.html', styles: [':host { display: contents; }'] })
export class BankSignInComponent {
    readonly busy = input(false);
    readonly error = input('');
    readonly signIn = output<SessionSignInRequest>();
    username = '';
    password = '';
    submitSignIn() {
        if (this.busy() || !this.username.trim() || !this.password)
            return;
        this.signIn.emit({ username: this.username.trim(), password: this.password });
        this.password = '';
    }
}
