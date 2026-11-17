# Sexual Health App

A JavaFX-based desktop application for sexual health management.

## Setup Instructions

### Prerequisites
- Java 21 or higher
- Git
- IntelliJ IDEA (with Maven support)

### Getting Started

1. **Create a project directory**
    - Create a folder where you want to store the project (e.g., `Documents/IntelliJ/`)

2. **Open the directory in IntelliJ**
    - Launch IntelliJ IDEA
    - File → Open
    - Select the folder you just created

3. **Set up SSH keys (first time only)**
    - Open the terminal in IntelliJ (`Alt+F12` or View → Tool Windows → Terminal)
    - Generate an SSH key:
```bash
   ssh-keygen -t ed25519
```
- Press Enter 3 times (default location, no passphrase)
- Copy your public key:
```bash
   cat ~/.ssh/id_ed25519.pub
```
- Go to https://git.soton.ac.uk
- Click your profile icon → Preferences → SSH Keys
- Paste the key and click "Add key"

4. **Clone the repository via terminal**
    - In the IntelliJ terminal, run:
```bash
   git clone git@git.soton.ac.uk:comp2300-group11/sexual-health.git
```

5. **Open the cloned project**
    - File → Open
    - Navigate to the `sexual-health` folder (created by the clone)
    - Click OK

6. **Load Maven dependencies**
    - IntelliJ should automatically detect `pom.xml` and load the project
    - If not: right-click on `pom.xml` → "Add as Maven Project"
    - Wait for all dependencies to download (this may take a few minutes)

7. **Configure environment variables (.env file)**
    - The application requires a `.env` file in the project root directory for database and email configuration
    - **Copy the example file:** Copy `.env.example` to `.env` in the project root:
    ```bash
    cp .env.example .env
    ```
    - **Or create manually:** If `.env.example` doesn't exist, create a `.env` file with the following required variables:
    ```env
    # Database Configuration
    DB_URL=jdbc:postgresql://your-database-host/database-name?sslmode=require
    DB_USERNAME=your-database-username
    DB_PASSWORD=your-database-password

    # Email/SMTP Configuration
    SMTP_HOST=smtp.gmail.com
    SMTP_PORT=587
    SMTP_USERNAME=your-email@gmail.com
    SMTP_PASSWORD=your-app-password
    MAIL_FROM=your-email@gmail.com
    ```
    - **Important:** 
      - For Gmail, you need to use an [App Password](https://support.google.com/accounts/answer/185833) instead of your regular password
      - The `.env` file should **NOT** be committed to Git (it should be in `.gitignore`)
      - Each team member needs their own `.env` file with valid credentials
    - **Note:** If email configuration is missing or incorrect, you'll see warnings in the console and emails will not be sent

8. **Run the application**
    - Run from Maven: `mvn javafx:run` (uses `app.Main` as entry point from `pom.xml`)
    - Or in IntelliJ: open `src/main/java/app/Main.java` and run the `main` method (▶), or press `Shift+F10`

### Project Structure
```
sexual-health/
├── src/main/java/
│   ├── app/              # Entry point (Main), DB, Data, SceneManager
│   ├── controller/       # Login, SignUp, Main, Home, Tracking
│   ├── dao/              # Data access (Appointment, User, etc.)
│   ├── model/            # Domain models
│   ├── service/          # Auth, Tracking, email, NotificationScheduler
│   ├── view/             # UI (Navigator, booking, reminders, notification, ui)
│   └── database/         # Legacy DB helper (primary: app.DB)
├── src/main/resources/   # FXML, CSS, email templates
├── docs/                 # SETUP, ARCHITECTURE, BASICS
├── pom.xml               # Maven build; mainClass: app.Main
└── .env.example          # Template for per-machine .env (DB + SMTP)
```

#### Why This Structure?

**Maven Standard Layout:**
- `src/main/java/` is Maven's required directory for Java source files. Maven won't compile code placed anywhere else.

**Package structure (MVC-style):**
- `app` – application entry and shared state; `controller` – FXML and flow; `dao` – database; `model` – data; `service` – business logic; `view` – screens and UI.

**Separation of Concerns:**
- `src/` - Source code and application files
- `docs/` - Documentation, design documents, and project reports
- `pom.xml` - Dependency management and build configuration (lives at project root)

### Configure Git Identity

Before making your first commit, configure your Git identity:
```bash
    git config --global user.email "your.email@soton.ac.uk"
    git config --global user.name "Your Name"
```

Replace with your actual Southampton email and name. This is required for Git to track who made each commit.

**Note:** This is a one-time setup per computer - you only need to do this once.

### Technologies Used
- Java 21
- JavaFX 21.0.5
- MaterialFX 11.17.0
- Maven

### Troubleshooting

#### Build and Runtime Issues
- If the project doesn't recognize JavaFX, reload Maven: right-click `pom.xml` → "Reload Maven Project"
- Make sure you have Java 21 installed: File → Project Structure → Project SDK
- If SSH clone fails, you may need to verify your SSH key is properly added to GitLab

#### Database Connection Issues
- If you see `[DB] WARNING` messages about missing configuration, check that your `.env` file exists and contains `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`
- If database connection fails, verify:
  - The database server is accessible from your network
  - Firewall allows connections to the database port
  - Credentials in `.env` are correct

#### Email Sending Issues
- **"Could not convert socket to TLS" error:**
  - This indicates a TLS/SSL handshake failure, often caused by:
    - Network/firewall blocking port 587 or TLS connections
    - Antivirus software interfering with secure connections
    - Corporate/university network restrictions
    - Incorrect `SMTP_HOST` or `SMTP_PORT` in `.env`
  - **Automatic Recovery Features:**
    - The application automatically retries failed TLS connections (up to 2 attempts with exponential backoff)
    - For Gmail users: If STARTTLS (port 587) fails, the app automatically tries SSL (port 465) as a fallback
    - These features significantly reduce TLS errors caused by transient network issues
  - **Solutions if automatic recovery fails:**
    - Try using a different network (e.g., mobile hotspot) to test if it's network-related
    - Verify `SMTP_HOST` and `SMTP_PORT` are correct in `.env`
    - Check if your antivirus/firewall is blocking Java applications from making secure connections
    - For Gmail, ensure you're using an App Password (not your regular password) in `SMTP_PASSWORD`
    - If your network blocks port 587, Gmail will automatically try port 465 (SSL) - no configuration needed
- **"Authentication failed" error:**
  - Check that `SMTP_USERNAME` and `SMTP_PASSWORD` in `.env` are correct
  - For Gmail, make sure you're using an App Password, not your regular account password
- **Email configuration warnings:**
  - If you see `[Email] WARNING` messages, the application will still run but emails won't be sent
  - Verify all email-related variables in `.env` are set correctly
- **Enable email debugging:**
  - To see detailed email connection logs, run: `mvn javafx:run -Dmail.debug=true`