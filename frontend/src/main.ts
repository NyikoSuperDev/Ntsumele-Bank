import { bootstrapApplication } from '@angular/platform-browser';
import { provideHttpClient, withXsrfConfiguration } from '@angular/common/http';
import { BankingWorkspaceComponent } from './app/workspace/banking-workspace.component';
bootstrapApplication(BankingWorkspaceComponent, {providers:[provideHttpClient(withXsrfConfiguration({cookieName:'XSRF-TOKEN',headerName:'X-XSRF-TOKEN'}))]}).catch(error => console.error('Application startup failed',error));
