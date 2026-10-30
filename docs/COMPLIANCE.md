# South African compliance assessment

Assessment date: **29 October 2026**. Ntsumele Bank is a fictional institution in this project. This is an engineering assessment for a runnable reference implementation, not a legal opinion, licensing assessment, or certification that a real bank complies with its obligations. Use synthetic data with the supplied demo. The bank's legal, privacy, payments, risk, and security owners must approve the institution-specific controls before live operation.

## POPIA

The [Protection of Personal Information Act 4 of 2013](https://www.justice.gov.za/legislation/acts/2013-004.pdf) provides the following relevant requirements:

| Provision | Requirement to assess |
| --- | --- |
| Sections 8–13, 15–18 | Accountability, minimality, an appropriate processing justification, specified purposes, compatible further processing, information quality, and collection notices. Consent is not the only possible justification. |
| Section 14 | Retention must have a lawful basis. Consider decision records, legal retention, restrictions, and deletion or de-identification when retention is no longer authorised. |
| Sections 19–21 | Appropriate organisational and technical security, authorised processing, and written security arrangements with operators. |
| Sections 23–25 | Identity-verified access and correction processes, subject to applicable restrictions. |
| Sections 26–35, 55, 57–58, 72 | Assess special/children's information, Information Officer responsibilities, any prior authorisation, and conditions for overseas transfers. |

Implementation mapping and recommended institutional work:

- **Implemented:** account-scoped server queries, session authentication, CSRF protection, masked account numbers, bounded dispute text, and a separate dispute/history model. The dispute submission accepts a transaction identifier and claim details; financial values come from the server's ledger projection.
- **Institutional work:** document the processing inventory and justified purposes for customer claims, fraud handling, staff review, and audit. Publish a collection notice describing recipients, operators, contact routes, rights, and retention. A checkbox in a dispute form is not a substitute for this assessment.
- **Institutional work:** approve a record schedule by record class: original ledger events, claims, customer correspondence, status history, audit, logs, and idempotency keys. Define legal holds, restricted processing, evidence-preserving correction, controlled deletion, and backup expiry. This project does not schedule erasure or implement a data-subject request workflow. Closing a dispute is not an erasure request.
- **Institutional work:** prevent customers from entering PINs, CVVs, passwords, full card numbers, or unnecessary sensitive information in descriptions. Extend redaction and staff training before adding attachments. Assess PCI DSS scope with the institution's card-security owner if cardholder data or integrations are introduced.
- **Institutional work:** contract with hosting, identity, logging, and banking providers; assess their access and subprocessors. Approve hosting regions and overseas transfers. Keep production data out of development, screenshots, source archives, and support logs.

For suspected unauthorised access/acquisition, activate the Information Officer and incident response team. The Regulator's [security compromise guidance](https://inforegulator.org.za/2025/08/19/fact-sheet-handling-of-security-compromises/) explains that reporting is not limited to high-risk compromises, must be made as soon as reasonably possible, and need not await completion of the investigation. Operators must inform the responsible party immediately. The Regulator has required use of its [eServices reporting channel since 1 April 2025](https://inforegulator.org.za/wp-content/uploads/2025/04/MEDIA-STATEMENT-INVITATION-TO-REPORT-SECURITY-COMPROMISES-THROUGH-THE-eSERVICES-PORTAL-.pdf). Do not replace this with an assumed generic 72-hour deadline. Assess affected-person notices and any lawful delay with counsel, and retain notification evidence and subsequent updates.

## Financial-sector governance and resilience

The [PA/FSCA publication of Joint Standard 1 of 2023](https://www.resbank.co.za/content/dam/sarb/publications/prudential-authority/pa-public-awareness/covid-19-response/2023/joint-communication-4-of-2023/Joint%20Communication%204%20of%202023-%20Publication%20of%20the%20Joint%20Standard%20-%20IT%20Gov%20and%20Risk.pdf) confirms IT governance and risk-management requirements and commencement on 15 November 2024. [Joint Notice 1 of 2024](https://www.resbank.co.za/content/dam/sarb/publications/prudential-authority/pa-public-awareness/covid-19-response/2024/joint-comms-5-of-2024/Joint%20Notice%201%20of%202024%20-%20Commencement%20-%20Cybersecurity%20Joint%20Standard.pdf) sets 1 June 2025 as the effective date for Joint Standard 2 of 2024 on cybersecurity and cyber resilience. Determine the precise scope for the licensed entity and obtain the full current standards through the [SARB publication page](https://www.resbank.co.za/en/home/publications/publication-detail-pages/prudential-authority/pa-public-awareness/Communication/2024/Joint-Communication-2-of-2024-Publication-of-the-Joint-Standard-Cybersecurity-and-cyber-resilience).

Recommended evidence for this service includes an approved risk assessment and accountable owners; segregation of customer, reviewer, database, and deployment duties; periodic access review; secure change approval; independent security testing; vulnerability management; supplier controls; service monitoring; incident exercises; and recovery evidence with approved business objectives. These are organisational deliverables in addition to the source code.

The [FSCA website](https://www.fsca.co.za/) currently lists **Joint Notice 2 of 2026** concerning the incident notification template under both standards. The linked download could not be parsed by the research tool during this assessment. Before launch, obtain the signed notice, current reporting template, applicable channels and deadlines directly from the authorities and incorporate them into the incident runbook. Do not assume the older consultation template or a POPIA notification satisfies financial-sector reporting. Also assess institution-specific banking conduct, complaints, outsourcing, AML/CFT, fraud escalation, evidential retention, and applicable licence conditions with the relevant owners.

## Payment-scheme disputes and customer complaints

Creating a case in this application **does not submit a chargeback, reverse an entry, stop a debit order, refund a customer, or guarantee a successful outcome**. The four application statuses describe internal case handling. A real institution needs a separately authorised, reconciled payment-scheme integration and case mapping.

| Payment type | Required institutional decision |
| --- | --- |
| Visa card | Map the claim to the applicable dispute condition, geography, product, evidence and timing rules in the institution's current [Visa Core Rules and Product and Service Rules](https://cis.visa.com/content/dam/VCOM/download/about-visa/visa-rules-public.pdf) and issuer/processor agreements. |
| Mastercard card | Use the current issuer rules and agreements; the public [Mastercard Chargeback Guide, Merchant Edition](https://www.mastercard.com/content/dam/mccom/shared/business/support/rules-pdfs/chargeback-guide.pdf) illustrates that conditions, stages and regional rules differ. A merchant guide alone is not an issuer operating specification. |
| South African debit order | Distinguish DebiCheck, Registered Mandate and EFT. [PASA's debit order resource](https://pasa.org.za/pasa-resources/pasa-debit-orders/) explains the mandate distinctions and links its [2026 dispute rule-change FAQ](https://pasa.org.za/wp-content/uploads/2026/04/PASA_DODE_FAQ_v3.pdf). That PDF timed out in this research session; obtain the current member rules and implementation circulars before encoding eligibility. |
| EFT, ATM, transfers and other rails | Agree rail-specific fraud/recall routes, evidential requirements and customer communications with operations and the ledger/provider owners. |

Do not apply a universal 120-day card window or historical 40/365-day debit-order window to every transaction. Introduce a versioned rules service keyed by payment rail, scheme, region, relevant dates, reason, mandate, and transaction state. Save the applied rule version and deadline with the case, alert before expiry, and test calendar boundaries. The current project does not implement these scheme eligibility rules; its eligibility controls are technical safety rules for the demo.

Provide an acknowledged complaint, progress communication, a reasoned outcome, and a published escalation route with institution-approved timelines. The [National Financial Ombud Scheme Banking Division](https://nfosa.co.za/nfo-divisions/banking/) describes independent resolution for eligible complaints after the bank's usual complaint channels have been exhausted. Validate the institution's participation and jurisdiction before presenting escalation information as applicable to a customer. A terminal internal `RESOLVED` status must not silently remove external complaint or appeal rights.

## Release acceptance record

Before a real deployment, record approval owners, evidence links, outstanding risks, and review dates for: legal applicability; POPIA assessment and notices; retention/holds/rights handling; scheme rules and deadline tests; IAM/MFA and staff access; penetration testing; database durability and audit protection; provider reconciliation; incident reporting; backup restoration; and customer complaint escalation. Track these as release requirements. Passing the application's tests is evidence of selected software behaviours, not regulatory approval.
