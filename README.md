# WalletWise Java backend

Spring Boot API replacement for the WalletWise React frontend. MongoDB is configured to match the original repository (`walletwise` database on port 27017); the API's current development data remains in memory until the persistence repositories are introduced.

## Run

```powershell
.\gradlew.bat bootRun
```

The API listens on `http://localhost:5000`, accepts `/api` and `/api/v1` routes, and allows the React dev servers on ports 3000 and 5173.

## Database

The local MongoDB URI is `mongodb://localhost:27017/walletwise`. On startup the app creates the original project's collections (`users`, `transactions`, `budgets`, `savingGoals`, `subscriptions`, `investments`, `wallets`, and `transactionActivities`) and their key unique/query indexes. Set `MONGODB_URI` to use MongoDB Atlas instead. If MongoDB is not installed locally, run `docker compose up -d mongodb`.

Implemented endpoints: authentication (`/auth/register`, `/auth/login`, `/auth/me`), profile, transactions, budgets, dashboard summary, and `/api/health`.

Set the frontend API base URL to `http://localhost:5000/api` (or use `/api/v1` if its client is versioned).
