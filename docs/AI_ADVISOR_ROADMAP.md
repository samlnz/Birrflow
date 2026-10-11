# AI Capital Growth Advisor — 10x plan

The advisor used to be a single prompt: one snapshot of headline numbers, pasted in front of the
question, answered once. That caps it at "smart chatbot". The plan below turns it into an
**agent that works on the user's real data**, in four layers. Phase 1 is implemented in this
change; phases 2–4 are the roadmap.

## What was holding it back

| Limitation | Effect |
|---|---|
| Snapshot built from the dashboard's *filtered* transactions (default: today only) | "This month's income/expenses" were really today's figures |
| Internal transfers counted as income | Inflated income, broke rule 3 of its own prompt |
| One-shot prompt, no tools | The model could only talk about the 10 numbers it was given; it guessed the rest |
| No memory of patterns | No forecast, no recurring bills, no anomalies, no trends |
| Free-form JSON parsing | Analyses often fell back to canned opportunities |
| Hard-coded token counts | Diagnostics were fiction |

## Phase 1 — implemented

**1. On-device intelligence engine** (`ai/AdvisorIntelligence.kt`, no API cost, runs live)
- 6-month income / expense / net history and 3-month savings rate
- 30/60/90-day cash forecast from the 90-day daily net trend, and "days until zero"
- Recurring payment detection (same payee, stable amount, steady 5–40 day rhythm) with next due date
- Anomaly detection for debits far above the user's usual size
- Top payees, income by source, spending by weekday, month-to-date spend vs last month
- Savings-goal pacing and overdue capital-plan milestones
- **Smart Alerts** on the advisor's first tab (runway risk, spending spikes, unusual debits,
  upcoming bills, uncategorized backlog, savings rate, goals behind) — each one opens a chat question

**2. Grounding fixed and deepened**
- Snapshot uses every transaction, month-to-date, internal transfers excluded
- The whole deep context plus the user's own **category descriptions** go into a real
  `systemInstruction` (not glued to the prompt)
- Capital analysis runs in JSON mode (`responseMimeType: application/json`) and is told to attack
  the detected recurring payments / anomalies while keeping the forecast above the emergency reserve

**3. Tool-using agent in Consultation** (`ai/AdvisorAgent.kt`, Gemini function calling)
- Read tools: `get_transactions`, `get_spending_breakdown`, `get_cash_flow_trend`,
  `get_recurring_payments`, `forecast_balance`, `get_capital_plan`
- Action tools (only when the user asks): `add_capital_plan_milestone`, `update_milestone_status`,
  `create_savings_goal`, `set_transaction_category` — e.g. *"categorize my uncategorized expenses"*
  files debits using the category descriptions
- Up to 6 tool rounds per answer; model turns are echoed back unchanged so thought signatures survive
- Every answer shows what the agent looked up or changed
- **Live web mode**: Google Search grounding for current rates, T-bill auctions and prices, with sources

**4. Reliability**
- Retries with backoff on 429/5xx, real token usage from `usageMetadata` in Diagnostics
- "Add to Capital Plan" fixed (locking, de-duplication, deadline from the opportunity's timeframe)

## Phase 1b — implemented (market, wealth, learning)

**Ethiopian market data** — dated, sourced figures (NBE policy rate, inflation, T-bill yields,
deposit/lending rates, interest tax, USD, gold, fuel), refreshed daily from the web via Google
Search grounding, editable by the user. The advisor may only quote these (or a cited source), and
hard-coded "10–14%" claims are gone. Tool: `get_market_rates`.

**Real-return ranking** — savings, wallet savings, term deposit, interest-free mudarabah, T-bills,
paying down debt, own-business stock, gold, ESX shares and equb ranked by return after inflation,
interest tax and costs, filtered by the user's risk level, lock-up limit and interest-free choice.
Tool: `rank_investment_options`.

**Net-worth ledger** — equb, gold, shares, deposits, receivables, stock, property, loans and payables
on the "Me" screen; net worth = balances + assets − debts. Tools: `get_net_worth`, `record_wealth_item`.

**Learning categorization** — learns from every debit the user files plus category descriptions;
suggested category first on the notification and in the categories tab; opt-in auto-filing for
payees always filed the same way; accuracy tracked. Tool: `suggest_categories`.

**Weekly briefing & decision tracking** — every 7 days, on the phone: net worth change (also after
inflation), week in/out, warnings, best place for idle cash, upcoming bill, plan progress. Plan
milestones are measured from transactions (spending vs baseline, payments to a payee) and the track
record goes into the prompt.

**About Me** — household, work, income stability, business numbers, interest-free preference, risk
quiz, horizon, lock-up limit, emergency months, goals, obligations, language. Drives ranking and the
prompt. Tools: `get_user_profile`, `update_user_profile`.

**Memory & learning loop** — long-term notes (facts, preferences, goals, constraints, lessons) the
agent saves with `remember_memory` / `forget_memory` / `recall_memory`; the most relevant are in every
instruction. Older chat turns are folded into a running summary with durable facts extracted. 👍/👎
on answers; a 👎 reason becomes a lesson. All editable in "Me → AI Memory".

## Phase 2 — next (weeks)

- **Streaming answers** via `streamGenerateContent` (SSE) so long answers appear word by word
- **What-if simulator**: "If I cut Food by 20% and move 50,000 ETB to a 13% deposit, where am I in
  6 months?" — deterministic projection, the model only explains it
- **Structured output schemas** (`responseSchema`) for the analysis to remove the fallback path entirely

## Phase 3 — next (months)

- **Retrieval over the full SMS history** with Gemini embeddings stored locally, so questions like
  "when did I last pay the generator mechanic?" work across years of data
- **Multi-agent review**: a second "risk officer" pass checks every recommendation against the
  emergency reserve, runway and stated risk appetite before it is shown
- **Voice in Amharic and English** with the Gemini Live API
- **Receipt / invoice photos** parsed into transactions with multimodal input

## Phase 4 — privacy & cost

- On-device model (Gemini Nano via AICore where available) for categorization and alerts so most
  daily work never leaves the phone; the cloud model is used only for deep analysis and chat
- Context caching for the large system instruction to cut cost and latency of follow-up questions
- Per-feature budgets and a monthly cost view in Diagnostics
