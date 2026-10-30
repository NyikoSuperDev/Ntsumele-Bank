# Feature-prefixed class-type packages

The application is organized by banking capability. Each feature keeps its models, repositories, API contracts, and business actions together. Within each feature, class-type subpackages make each class’s role visible, using the pattern `za.co.ntsumele.bank.<feature>.<feature><Type>`. The application remains a single deployable banking application with logical domain boundaries and one relational database.

## Backend features

The root package is `za.co.ntsumele.bank`; `NtsumeleBankApplication` composes the application and its clock. Feature roots remain lowercase. Subpackages use the requested feature-prefixed camelCase names, such as `disputeController`, `accountRepository`, and `identityConfig`.

```text
backend/src/main/java/za/co/ntsumele/bank/
├── account/
│   ├── accountController/
│   │   └── AccountsController.java
│   ├── accountDto/
│   │   └── AccountSummaryResponse.java
│   ├── accountMapper/
│   │   └── AccountResponseMapper.java
│   ├── accountModel/
│   │   └── BankAccount.java
│   ├── accountRepository/
│   │   └── AccountRepository.java
│   └── accountService/
│       └── AccountInquiryService.java
├── audit/
│   ├── auditModel/
│   │   └── AuditEvent.java
│   ├── auditRepository/
│   │   └── AuditEventRepository.java
│   └── auditService/
│       └── AuditTrailService.java
├── demo/
│   ├── demoInitializer/
│   │   └── DemoBankDataInitializer.java
│   └── demoRepository/
│       └── DemoSeedRepository.java
├── dispute/
│   ├── disputeController/
│   │   ├── DisputeReviewsController.java
│   │   └── DisputesController.java
│   ├── disputeDto/
│   │   ├── DisputeCaseResponse.java
│   │   ├── DisputeCountsResponse.java
│   │   ├── DisputeHistoryEventResponse.java
│   │   ├── DisputeInformationRequest.java
│   │   ├── DisputePageResponse.java
│   │   ├── DisputeStatusUpdateRequest.java
│   │   ├── DisputeSubmissionRequest.java
│   │   └── DisputeSummaryResponse.java
│   ├── disputeMapper/
│   │   └── DisputeResponseMapper.java
│   ├── disputeModel/
│   │   ├── DisputeCase.java
│   │   ├── DisputeCaseCounts.java
│   │   ├── DisputeHistoryEntry.java
│   │   ├── DisputeIdempotencyRecord.java
│   │   ├── DisputeReason.java
│   │   ├── DisputeSearchResult.java
│   │   └── DisputeStatus.java
│   ├── disputeRepository/
│   │   ├── DisputeCaseRepository.java
│   │   ├── DisputeHistoryRepository.java
│   │   └── DisputeIdempotencyRepository.java
│   └── disputeService/
│       ├── DisputeInformationService.java
│       ├── DisputeInquiryService.java
│       ├── DisputeLifecycleService.java
│       ├── DisputeReviewService.java
│       ├── DisputeSubmissionService.java
│       ├── IdempotencyCleanupService.java
│       └── IdempotencyRetentionService.java
├── failure/
│   └── failureException/
│       └── BankingRuleViolation.java
├── http/
│   ├── httpDto/
│   │   └── HttpProblemResponse.java
│   ├── httpFilter/
│   │   └── JsonRequestSizeFilter.java
│   ├── httpHandler/
│   │   └── BankingExceptionHandler.java
│   └── httpWriter/
│       └── HttpFailureWriter.java
├── identity/
│   ├── identityConfig/
│   │   ├── SecureTransportConfig.java
│   │   └── SessionSecurityConfig.java
│   ├── identityController/
│   │   └── SessionsController.java
│   ├── identityDto/
│   │   ├── SessionCsrfResponse.java
│   │   └── SessionIdentityResponse.java
│   ├── identityFilter/
│   │   ├── HttpsEnforcementFilter.java
│   │   └── SignInAttemptLimitFilter.java
│   ├── identityModel/
│   │   └── BankIdentity.java
│   ├── identityRepository/
│   │   └── BankIdentityRepository.java
│   └── identityService/
│       ├── BankIdentityDetailsService.java
│       └── IdentityProfileService.java
├── transaction/
│   ├── transactionController/
│   │   └── TransactionsController.java
│   ├── transactionDto/
│   │   ├── TransactionActivityResponse.java
│   │   └── TransactionPageResponse.java
│   ├── transactionMapper/
│   │   └── TransactionResponseMapper.java
│   ├── transactionModel/
│   │   ├── LedgerTransaction.java
│   │   ├── TransactionDisputeStatus.java
│   │   └── TransactionSearchResult.java
│   ├── transactionPolicy/
│   │   └── TransactionEligibilityPolicy.java
│   ├── transactionRepository/
│   │   └── LedgerTransactionRepository.java
│   └── transactionService/
│       └── TransactionInquiryService.java
└── NtsumeleBankApplication.java
```

| Feature | Owns |
| --- | --- |
| `identity` | Authenticated identity, credential lookup, sign-in throttling, session/CSRF endpoints and access configuration |
| `account` | Account summaries and account-entitlement checks |
| `transaction` | Ledger projection queries, transaction activity views and eligibility policy |
| `dispute` | Customer submissions, case inquiry, staff decisions, requested information, ordered history and principal-scoped idempotency |
| `audit` | Recording significant actions with actors, times and changes |
| `failure` | The small, framework-independent banking failure contract |
| `http` | HTTP error serialization, exception-to-status translation and bounded JSON request bodies |
| `demo` | Trusted, atomic initialization of fictional evaluation data |

`http`, `failure`, and `demo` are narrowly scoped runtime/composition boundaries. They do not contain unrelated business utilities. There are no `common`, `shared`, `utils`, `helpers`, `managers`, `core`, `base`, or mandatory `impl` buckets.

## Feature-prefixed Java subpackages

| Subpackage inside a feature | Class role | Example |
| --- | --- | --- |
| `disputeController` | Resource HTTP endpoints | `dispute.disputeController.DisputesController` |
| `disputeService` | Focused business use cases and transactions | `dispute.disputeService.DisputeSubmissionService` |
| `disputeRepository` | Parameterized SQL, persistence and row mapping | `dispute.disputeRepository.DisputeCaseRepository` |
| `disputeModel` | Domain records, enums and internal query results | `dispute.disputeModel.DisputeCase`, `dispute.disputeModel.DisputeStatus` |
| `disputeDto` | Request and response contracts | `dispute.disputeDto.DisputeSubmissionRequest`, `dispute.disputeDto.DisputeCaseResponse` |
| `disputeMapper` | Domain-to-response conversions | `dispute.disputeMapper.DisputeResponseMapper` |
| `transactionPolicy` | Business eligibility decisions | `transaction.transactionPolicy.TransactionEligibilityPolicy` |
| `identityConfig` | Feature-owned Spring configuration | `identity.identityConfig.SessionSecurityConfig` |
| `identityFilter` | HTTP/security request filters | `identity.identityFilter.SignInAttemptLimitFilter` |
| `failureException` | Banking failure types | `failure.failureException.BankingRuleViolation` |
| `httpHandler` | HTTP exception handling | `http.httpHandler.BankingExceptionHandler` |
| `httpWriter` | HTTP error serialization | `http.httpWriter.HttpFailureWriter` |
| `demoInitializer` | Trusted demo initialization | `demo.demoInitializer.DemoBankDataInitializer` |

Only subpackages containing relevant classes are created; every feature does not need every class type. The application entry point remains at the root so Spring scans all features. All five dispute services remain together in `dispute.disputeService`, preserving the package-private lifecycle writer and inquiry methods without widening their visibility. The lifecycle writer retains mandatory transaction propagation.

## Focused use cases

The former broad dispute service has been replaced by four independently named responsibilities:

- `DisputeInquiryService` retrieves authorized cases and complete timelines.
- `DisputeSubmissionService` validates and submits one customer claim with the original lock order, idempotency hash and atomic database writes.
- `DisputeReviewService` applies permitted staff review decisions.
- `DisputeInformationService` accepts a customer's requested information and resumes review.

The package-private `DisputeLifecycleService` shares the exact versioned case-state/history/audit write operation used by review and information responses. Its transaction propagation is `MANDATORY`: it must join an existing use-case transaction. It exposes no independent HTTP operation. The services are not wrappers around an old monolithic service.

Controllers bind HTTP input and call the relevant use case. Services enforce banking rules and transaction boundaries. Repositories own SQL and JDBC row mapping and consistently end in `Repository`. Mappers convert domain records to output views without database access. These responsibilities remain within each owning feature rather than being collected into application-wide technical folders.

The demo initializer calls `demo.demoRepository.DemoSeedRepository` for the database advisory lock and feature repositories for seeded records; a transaction encloses the complete seed so concurrent startups cannot create partial/duplicate demo records. Existing identities indicate an initialized database and are retained.

The models are immutable Java records backed by JDBC; they are not JPA entities. The ledger repository has no transaction-update operation for dispute workflows. Existing account authorization and relational read queries are retained. These are logical boundaries in a relational monolith, not a claim that each feature has isolated tables or can be deployed independently.

## Frontend features and components

The Angular client uses standalone components and feature-owned clients. Its source does not need an NgModule for each folder.

```text
frontend/src/app/
├── workspace/
│   └── banking-workspace.component.{ts,html,spec.ts}
├── identity/
│   ├── bank-sign-in.component.{ts,html}
│   ├── session-access-client.ts
│   └── session-identity.ts
├── account/
│   ├── account-overview.component.{ts,html}
│   ├── account-inquiry-client.ts
│   └── account-summary.ts
├── transaction/
│   ├── transaction-activity.component.{ts,html}
│   ├── transaction-activity-client.ts
│   ├── transaction-activity.ts
│   └── transaction-display.ts
├── dispute/
│   ├── dispute-history.component.{ts,html}
│   ├── dispute-history-preview.component.{ts,html}
│   ├── dispute-case-dialog.component.{ts,html,spec.ts}
│   ├── dispute-case-client.ts
│   ├── dispute-case.ts
│   └── dispute-display.ts
├── http/
│   ├── session-http-client.ts
│   └── http-failure-message.ts
└── icon/
    └── bank-icon.component.ts
```

Feature client tests live beside their clients. `BankingWorkspaceComponent` composes navigation, session state and the feature screens. `BankSignInComponent` owns sign-in fields. `DisputeCaseDialogComponent` owns case drafts, the current retry key, versioned case actions, focus trapping and draft cleanup. Each component/client name describes its resource or action. `SessionHttpClient` owns only the session HTTP protocol: fresh CSRF tokens, POST headers and bounded request timeouts. `BankIconComponent` is an explicit visual primitive, not a general shared business module.

## Naming conventions

| Kind | Convention | Implemented examples |
| --- | --- | --- |
| Java package | Lowercase feature, then feature-prefixed camelCase class role | `identity.identityConfig`, `account.accountService`, `transaction.transactionRepository`, `dispute.disputeDto` |
| API controller | Plural resource or specific resource action | `AccountsController`, `TransactionsController`, `DisputeReviewsController` |
| Business action | Narrow noun/action/context plus `Service` | `AccountInquiryService`, `DisputeSubmissionService`, `DisputeReviewService` |
| Persistence | Domain name plus `Repository` | `LedgerTransactionRepository`, `DisputeIdempotencyRepository`, `AuditEventRepository` |
| Input contract | Exact business action plus `Request` | `DisputeSubmissionRequest`, `DisputeInformationRequest`, `DisputeStatusUpdateRequest` |
| Output contract | Specific view plus `Response` | `SessionIdentityResponse`, `AccountSummaryResponse`, `TransactionActivityResponse`, `DisputeCaseResponse` |
| Angular component | Named screen/capability plus `Component` | `BankSignInComponent`, `TransactionActivityComponent`, `DisputeHistoryComponent` |
| Angular client | Feature and action plus `Client` | `AccountInquiryClient`, `SessionAccessClient`, `DisputeCaseClient` |
| Types and variables | PascalCase types; descriptive lowerCamelCase members | `DisputeStatus`, `transactionId`, `requestKey`, `expectedVersion` |
| Constants | UPPER_SNAKE_CASE | `MAX_BYTES`, `DISPUTE_REASONS` |
| File names | Java type names; Angular kebab-case | `DisputeSubmissionService.java`, `dispute-case-dialog.component.ts` |

There are no redundant service interface/`Impl` pairs or mixed `Dao`/`Store`/`Provider` persistence names. One public Java type belongs in each file. Related TypeScript view/request interfaces may share their feature model file.

## Physical build units

| Build unit | Name | Boundary |
| --- | --- | --- |
| Maven application artifact | `ntsumele-bank` | The runnable Spring Boot deployment, including the compiled Angular client |
| npm package | `ntsumele-bank-frontend` | The Angular source, tests and production build |

Both names already use kebab-case. This change does not create artificial Maven modules or separate microservices merely to match the folder count. If an independently deployed worker, API, or integration is introduced, its module should be named for that physical responsibility. The current domain features are logical modules inside the existing application. Docker, Maven and npm entry points remain the same.

## Dependency and compatibility checks

Five `FeatureArchitectureTest` checks enforce named feature ownership, correct `feature/featureType/ClassName.java` placement, repository-only SQL/JDBC, transport-free action services, separated domain/output contracts and acyclic Java imports among the business features. The production feature imports remain directed: transaction uses account authorization; dispute uses identity locking, account authorization, transaction inquiry/eligibility and audit recording. Runtime HTTP/failure contracts and demo composition are explicitly outside the business dependency-cycle check.

The PostgreSQL integration suite covers the original banking behaviours and independent-connection persistence, including concurrent duplicates, replay after closure, immutable ledger amounts, account isolation, real session/CSRF handling and complete historical records. Frontend checks cover the feature clients, composition, stale responses, session cleanup, immutable submission payloads, idempotent retries, keyboard closing rules and late-response suppression after dialog destruction.

The feature refactor preserves API paths, JSON property names, enum values, demo identities and logical database identifiers. PostgreSQL now replaces H2: the driver, Flyway database module, datasource settings and initial PostgreSQL schema are database-specific. The old H2 file profile is removed; a new PostgreSQL target is required, with a controlled transfer for any existing data. The integration profile uses a separate disposable `_test` database and the real PostgreSQL driver rather than an in-memory substitute. A dispute still references its original transaction and never changes the ledger amount, account, or reference.

Transaction activity derives `TransactionDisputeStatus` from the existing dispute records independently of the ledger `status`: `NOT_DISPUTED`, `DISPUTED`, or `DISPUTE_RESOLVED`. Repository-side filtering keeps authorized totals and pagination aligned. The additive `latestDisputeId` enables historical navigation while `activeDisputeId` retains its existing meaning. No mutable ledger flag or migration is introduced; see [transaction status rules](TRANSACTION-STATUS.md).

The architecture/source checks support maintainability; they do not replace institution-specific security, operational and compliance validation described in [OPERATIONS.md](OPERATIONS.md) and [COMPLIANCE.md](COMPLIANCE.md).

Dispute list DTOs now contain bounded summaries and pagination metadata, with independent aggregate-count responses. The case-reference sequence and retention worker stay inside the dispute feature. Identity configuration owns secure transport validation and its HTTPS filter. New top-level types retain the requested class/author/year Javadoc and specific descriptions. Full API list-shape changes and secure deployment requirements are in [API.md](API.md), [OPERATIONS.md](OPERATIONS.md) and [RELEASE.md](RELEASE.md).
