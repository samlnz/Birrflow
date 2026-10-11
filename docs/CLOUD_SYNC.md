# Live Multi-Phone Sync Guide (Internet Cloud Mesh)

BirrFlow keeps all of a person's Android phones in continuous live sync over the internet.
A bank SMS received on Phone 1 shows up on Phone 2 and Phone 3 within seconds, aggregating all accounts into one single live dashboard.

---

## 1. Zero-Cost Free Tier (Google Firebase Spark Plan)

- **100% Free Forever**: Firebase Spark Tier provides **50,000 document reads** and **20,000 document writes** every single day for free with **no credit card or billing required**.
- **End-to-End Encryption**: Every transaction payload is encrypted locally with AES-256-GCM using your private link code. Raw SMS text messages are **never** uploaded to the cloud.

---

## 2. Step-by-Step Setup Guide

### Step 1: Create Free Firebase Project (Takes 2 minutes)
1. Go to [console.firebase.google.com](https://console.firebase.google.com/) and click **Add project** (name it e.g. `birrflow-sync`).
2. Disable Google Analytics (optional) and click **Create Project**.
3. In the project sidebar:
   - Navigate to **Build > Authentication** -> Click **Get Started** -> Select **Anonymous** -> Toggle **Enable** -> Click **Save**.
   - Navigate to **Build > Firestore Database** -> Click **Create database** -> Choose Location -> Start in **Test mode** (or Production mode with rules from `firestore.rules`).

### Step 2: Configure in BirrFlow (2 Convenient Options)
You can configure Firebase in either of two ways:

- **Method A: Direct in-app connection (No rebuilding needed)**
  1. In the Firebase console, go to **Project Settings** (gear icon).
  2. Copy your **Project ID** (e.g. `birrflow-sync-12345`) and **Web API Key** (e.g. `AIzaSy...`).
  3. In BirrFlow on your phone, open the **Internet Sync** dialog (tap the cloud badge in the Total Cash Assets header).
  4. Paste your Project ID and Web API Key into the form and tap **Connect Firebase Cloud Mesh**.

- **Method B: google-services.json**
  1. In Firebase console Project Settings, under *Your apps*, click the Android icon.
  2. Enter Android package name: `com.aistudio.banksms.kzetqw`
  3. Download `google-services.json` and place it in the `app/` directory of the project.
  4. Recompile the app.

---

## 3. Pairing Phones with the 6-Digit PIN

1. **On Phone 1 (Primary device)**:
   - Tap the cloud sync badge or open **Phones** -> Tap **Generate 6-Digit Link** (or *First phone: start*).
   - Your private 6-digit sync PIN is generated instantly (e.g. `839-214`).
   - Tap **Copy PIN**.

2. **On Phone 2 & Other Phones**:
   - Install and open BirrFlow.
   - Tap **Internet Sync** -> Tap **Join with Code**.
   - Enter the 6-digit PIN (`839-214`) and tap **Join**.
   - Both devices immediately establish a real-time Firestore listener and sync all balances, SMS transactions, and asset totals!

