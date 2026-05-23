import { Routes } from '@angular/router';

import { anonymousGuard } from './core/auth/anonymous.guard';
import { authGuard } from './core/auth/auth.guard';

export const APP_ROUTES: Routes = [
  {
    path: 'login',
    canActivate: [anonymousGuard],
    loadComponent: () => import('./features/auth/login/login.page').then((m) => m.LoginPage)
  },
  {
    path: 'register',
    canActivate: [anonymousGuard],
    loadComponent: () =>
      import('./features/auth/register/register.page').then((m) => m.RegisterPage)
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./layout/app-shell.component').then((m) => m.AppShellComponent),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      {
        path: 'dashboard',
        loadComponent: () =>
          import('./features/dashboard/dashboard.page').then((m) => m.DashboardPage)
      },
      {
        path: 'transactions',
        loadComponent: () =>
          import('./features/transactions/list/transactions-list.page').then(
            (m) => m.TransactionsListPage
          )
      },
      {
        path: 'transactions/new',
        loadComponent: () =>
          import('./features/transactions/form/transaction-form.page').then(
            (m) => m.TransactionFormPage
          )
      },
      {
        path: 'transactions/import',
        loadComponent: () =>
          import('./features/transactions/import/transactions-import.page').then(
            (m) => m.TransactionsImportPage
          )
      },
      {
        path: 'transactions/:id/edit',
        loadComponent: () =>
          import('./features/transactions/form/transaction-form.page').then(
            (m) => m.TransactionFormPage
          )
      },
      {
        path: 'categories',
        loadComponent: () =>
          import('./features/categories/categories.page').then((m) => m.CategoriesPage)
      },
      {
        path: 'budgets',
        loadComponent: () =>
          import('./features/budgets/budgets.page').then((m) => m.BudgetsPage)
      },
      {
        path: 'recurring',
        loadComponent: () =>
          import('./features/recurring/recurring.page').then((m) => m.RecurringPage)
      },
      {
        path: 'profile',
        loadComponent: () =>
          import('./features/profile/profile.page').then((m) => m.ProfilePage)
      }
    ]
  },
  {
    path: '**',
    loadComponent: () =>
      import('./features/not-found/not-found.page').then((m) => m.NotFoundPage)
  }
];
