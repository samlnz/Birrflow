# Implementation Plan: Telebirr Reconciliation, Fast Indexer, Multi-Device 6-Digit Sync & Manual Cash Sources

This implementation plan outlines the architecture and execution strategy to resolve transaction discrepancies, accelerate initial SMS extraction, enable cross-device synchronization via an encrypted 6-digit sync code, add manual cash source tracking, and enhance the "Latest Bank Balances & Activity" card on the dashboard.

---

## 1. Diagnostics & Missing Transaction Reconciliation (Telebirr & All Banks)
- **Root Cause Analysis**:
  * Telebirr SMS messages arrive in various English and Amharic formats (transfers, merchant payments, deposits, cash-outs, airtime purchases, received money).
  * When intermediate messages were deleted or missed, the system previously had no mechanism to recognize unexplained balance jumps between consecutive messages.
- **Solution Strategy**:
  1. **Enhanced Telebirr & Amharic Parser Patterns**: Add comprehensive built-in regex rules in `SmsParserEngine.kt` for Telebirr deposits, cash-outs, bill payments, airtime, and Amharic transfer alerts (`ከ ... ETB ... ተቀብለዋል`, `ETB ... ወደ ... አስተላልፈዋል`).
  2. **Automated Balance Gap Recovery**:
     * In `FinancialRepository.kt`, when processing an SMS with `balanceAfter != null`, compare it against the previous recorded balance for that bank/wallet on that phone.
     * If `abs(actualBalance - expectedBalance) > 1.0 ETB`, automatically synthesize a **"Reconciled Balance Gap (Missing Intermediate Alert)"** transaction (Income or Expense) so that wallet totals are 100% mathematically reconciled to the exact penny.

---

## 2. Chunked Parallel Inbox Indexer & Real-Time Incremental Updates
- **Fast Batch Indexing**:
  * Update `SmsInboxReader.kt` to read system SMS in **chunks of 100 messages** using coroutine batch inserts (`Dispatchers.IO` + Room `@Transaction`).
  * Add a progress callback state (`scannedCount`, `totalCount`, `isScanning`) for visual feedback during the first-time scan.
- **Incremental Scanning**:
  * Store `lastScannedSmsTimestamp` persistently in Room / SharedPreferences.
  * Subsequent refreshes query SMS with `date > lastScannedSmsTimestamp`, completing in **< 50 milliseconds**.
- **Real-Time Reactive Updates**:
  * Leverage Room `Flow` streams so new incoming SMS immediately update all dashboard composables without UI lag or manual reloads.

---

## 3. Multi-Device Encrypted 6-Digit Code Synchronization
- **Cross-Device Bridge (`DeviceSyncManager.kt`)**:
  * **Generate Sync Code**: Create an encrypted 6-digit pair code (e.g. `839-204`) that packs and encrypts device records, financial sources, and historical transactions.
  * **Pair & Import**: On a secondary device, entering the 6-digit Sync Code decrypts and merges transactions and phone profiles into the main device database.
- **UI Integration**:
  * Add **"Sync Devices via Code"** modal accessible from the Top Bar and Settings screen with status indicators for paired devices.

---

## 4. Unified Bank Balances, Manual Cash & Expandable Accordion Card
- **Manual Cash Source Input**:
  * Add an **"➕ Add Manual Cash / Wallet"** dialog in "Latest Bank Balances & Activity" to track physical cash, office safes, or custom cash bags.
- **Total Combined Liquidity Sum Header**:
  * Prominently display `TOTAL LIQUIDITY & BANK BALANCES: ETB XXX,XXX.XX` across all bank accounts and manual cash sources combined.
- **Expandable Combined Accordion Cards**:
  * Each bank or manual cash source is rendered as an **Expandable Accordion Card**.
  * **Collapsed**: Shows Bank/Source icon, name, total balance across devices, transaction count.
  * **Expanded**:
    1. **Phone-by-Phone Breakdown Table**: Shows balance and transaction count per registered device.
    2. **Credit / Debit Activity Split**: Shows total credited (`+`), total debited (`-`), and internal transfers (`⇄`).
    3. **Manual Balance Adjustment Button**: Quickly set or adjust manual cash balances.

---

## Proposed Changes Across Files

| File Path | Changes |
| --- | --- |
| `parser/SmsParserEngine.kt` | Expand Telebirr English & Amharic regex rules (deposits, cash outs, bill payments). |
| `data/dao/FinancialDao.kt` | Add `@Insert` batch methods and `getLastTransactionForPhoneAndSource`. |
| `data/repository/FinancialRepository.kt` | Implement automatic balance discrepancy reconciliation and batch transaction processing. |
| `service/SmsInboxReader.kt` | Implement chunked batch inbox extraction and `lastScannedSmsTimestamp` incremental queries. |
| `sync/DeviceSyncManager.kt` | Create 6-digit encrypted sync code generator and payload import/export parser. |
| `ui/screens/DashboardScreen.kt` | Implement Total Liquidity Sum header, Manual Cash Source dialog, and Expandable Accordion cards for bank balances. |
| `ui/screens/AdminHubScreen.kt` & `SettingsScreen.kt` | Add Device Sync Hub interface and 6-digit code pairing modal. |

---

## Verification Plan

1. **Unit & Integration Testing**:
   - Run `./gradlew :app:testDebugUnitTest` to verify Telebirr parser rules, chunked indexing, and balance reconciliation logic.
2. **Compilation**:
   - Execute `compile_applet` to ensure error-free Jetpack Compose and Kotlin compilation.
3. **Manual Verification**:
   - Verify 6-digit sync code generation and import.
   - Verify manual cash source creation and Total Liquidity Sum calculation.
