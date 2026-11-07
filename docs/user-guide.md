# User guide

How to use DevOps Mobile Tools day to day: signing in, choosing what to work
as, opening a shell on an instance and keeping it safe. For building the app
and setting up AWS, see the [README](../README.md).

## Before you start

You need:

- An **IAM Identity Center** user (organization instance) assigned to at
  least one AWS account with a permission set that allows Session Manager.
  The README has a [minimal policy](../README.md#signing-in-with-iam-identity-center).
- Your **AWS access portal URL** (`https://….awsapps.com/start`) and the
  **region** where Identity Center lives. Both are in *Settings summary* on
  the Identity Center dashboard; ask your administrator if you don't know them.
- EC2 instances with the **SSM Agent online**: the instance role has the
  `AmazonSSMManagedInstanceCore` policy and the instance appears in
  *Systems Manager → Fleet Manager*.

No SSH keys, no inbound ports and no long-lived access keys are involved.

## The three steps to a shell

The home screen shows a progress bar under the logo: **1 · Sign in**,
**2 · Work as**, **3 · Instances**. The highlighted step is where you are.

### 1. Sign in

1. Fill in **AWS access portal URL** and **Identity Center region**
   (for example `eu-west-1`). The app remembers both, so you type them once.
2. Tap **Sign in**. Your browser opens on the AWS access portal.
3. The app shows a **code** in large type. Check that the browser shows the
   same code, then approve it there (sign in to the portal first if asked).
4. Go back to the app: it notices the approval by itself.

While waiting you can tap **Open the browser again** if you closed it, or
**Cancel** to start over.

Next time you open the app it checks the saved sign-in (*Welcome back*) and
skips this step while the sign-in is still valid.

### 2. Work as: account, role and region

Once signed in, the **Work as** card lists what you can work as:

1. If the lists are empty, tap **Load my accounts and roles**.
2. Pick the **Account**, then one of its **Roles**, then the **Region** where
   your instances run.
3. Tap **Use this role and region**.

A highlighted box confirms *Working as … in …* and shows the last four
characters of the temporary credentials and when they expire. They are
renewed automatically; you don't need to come back here when they do. The
choice is remembered for the next start.

To switch, change any picker and tap **Use this role and region** again.

Below, the **IAM Identity Center** card shows when the sign-in token
expires, with **Refresh now** (when the token can be refreshed) and
**Sign out**.

### 3. Instances

Tap **Show instances**. The list holds the EC2 instances whose SSM Agent is
online in the selected region.

- The chip at the top shows the role, account and region you are working as.
  **Tap it to change them.**
- **Filter** by name, instance id, IP address or platform with the search
  box; the ✕ clears it.
- **Refresh** with the button in the top bar or by pulling the list down.
- Each card shows the name, the instance id and private IP, the platform,
  and, if you already have a session on it, **Session open** (green) or
  **Session ended** (grey). The `>_` badge lights up in cyan while a session
  is open.
- Tap a card to open a shell on that instance, or to go back to its session
  if one is already open.

Instances are listed by their `Name` tag. If they show only their id, the
role lacks `tag:GetResources`.

**Nothing listed?** The app says so and reminds you of the usual causes:
the instance is stopped, its role lacks `AmazonSSMManagedInstanceCore`, the
agent is not registered in Fleet Manager, or you picked another region.

## The terminal

The session screen is a full terminal emulator: colours, `vim`, `htop`,
`less` and other full-screen programs work.

**Top bar**

- **←** goes back to the instance list. The session **keeps running**; tap the
  instance again to return to it.
- Under the instance name, a dot and a line show the state:
  - green: *Connected*;
  - amber: *Starting session…* or *Connection lost · reconnecting…*;
  - red: the session ended, with the reason.
- **Disconnect** ends the session on AWS and closes the screen. When the
  session has already ended it reads **Close**, next to **Reconnect**.

**Typing**

- **Tap the terminal** to bring up the keyboard. Suggestions are off, so
  commands are typed as they are.
- **Pinch** to zoom the text in or out. The size is remembered.
- Rotate the phone for more columns; the remote shell is resized to fit.

**Extra keys**

Two rows above the keyboard give the keys a phone keyboard lacks:

| Row 1 | `ESC` `/` `-` `\|` `HOME` `↑` `END` `PGUP` |
|---|---|
| **Row 2** | `TAB` `CTRL` `ALT` `~` `←` `↓` `→` `PGDN` |

- Arrows and page keys **repeat while held**.
- **CTRL** and **ALT**: a **tap** applies them to the next key only (the
  label turns cyan); a **long press** locks them on (cyan background,
  underlined) until you tap them again. For example, tap `CTRL` then type
  `c` to send Ctrl+C.

## Sessions in the background

Sessions don't stop when you leave the screen or the app:

- While any session is open, Android shows an **ongoing notification**
  listing them. Tap it to return to the app; **Disconnect all** ends every
  session. On Android 13 and later the app asks once for permission to show
  notifications; without it sessions still run, you just don't see them
  there.
- If the network drops or changes (Wi-Fi to mobile data, say), open sessions
  **reconnect by themselves** as soon as the network is back.
- Long sessions keep working after the first credentials expire: the app
  hands them the renewed ones.

A session ends when you disconnect it, when the shell exits (`exit`), or
when AWS ends it (idle timeout, or an administrator). The bottom of the
screen then offers the right next step.

## When something goes wrong

Errors explain what happened and offer one button for what to do next:

| You see | What it means | Button |
|---|---|---|
| *Your sign-in to IAM Identity Center has expired* | The SSO session ended | **Sign in again** |
| *Pick an account, a role and a region first* | No role is selected | **Back to account and role** / **Change role** |
| *No connection to AWS* | The phone is offline or AWS is unreachable | **Try again** / **Reconnect** |
| *The session ended on AWS* | Idle timeout or ended by an administrator | **Reconnect** starts a new session |
| *The shell on the instance exited* | You typed `exit`, or the shell stopped | **Reconnect** |
| *The instance did not answer the session handshake* | The agent didn't respond in time | **Reconnect** |
| An AWS access-denied message | The role lacks a permission | Ask your administrator, or pick another role |

If an instance you expect is missing, check *Fleet Manager* in the AWS
console first: the app only lists what Systems Manager reports as online.

## Security

- **App lock.** In the **Security** card on the home screen, turn on
  *Lock with biometrics or the screen lock* and choose how long the app may
  stay in the background before it locks: **Immediately**, **1 min**,
  **5 min** (default), **15 min** or **1 h**. A locked app shows how many
  sessions keep running behind it; unlock with your fingerprint, face or
  screen lock. The option is disabled until the phone has a screen lock.
- **No screenshots.** The app blocks screenshots and hides its content in
  the recent-apps view, so tokens and terminal output don't leak.
- **Storage.** The SSO token is stored encrypted with a key in Android
  Keystore and is never included in backups. Role credentials stay in
  memory only.
- **Sign out** on the home screen removes the saved sign-in from the phone.

## Light and dark

The app follows the system's light or dark setting. The terminal always
uses a dark background.
