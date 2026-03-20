# Welcome Message Feature

## Overview
Welcome Message feature automatically sends greeting messages to first-time contacts when they message you on WhatsApp.

## Features
- ✅ Auto-detect first-time contacts
- ✅ Single or Multiple message mode
- ✅ Custom delay between messages
- ✅ Track sent count
- ✅ Reset sent history

## Files Structure
```
welcomemessage/
├── WelcomeMessageData.kt      # Data classes & Room entities
├── WelcomeMessageDatabase.kt  # Room database & DAOs
├── WelcomeMessageManager.kt   # Business logic manager
├── WelcomeMessageActivity.kt  # UI Activity
└── README.md                  # This documentation
```

## How It Works

### Flow
1. User enables Welcome Message in settings
2. User adds one or more welcome messages
3. When a NEW contact messages for the first time:
   - System checks if contact has received welcome before
   - If not, sends welcome message(s)
   - Marks contact as "welcomed"
4. Subsequent messages from same contact won't trigger welcome

### Database Tables

#### `welcome_messages`
Stores the welcome messages configured by user.
| Column | Type | Description |
|--------|------|-------------|
| id | Int | Primary key (auto-generated) |
| message | String | The welcome message text |
| orderIndex | Int | Order for multiple messages |
| delayMs | Long | Delay before sending (ms) |
| isEnabled | Boolean | Is message active |
| createdAt | Long | Creation timestamp |

#### `welcome_message_sent`
Tracks which users have received welcome.
| Column | Type | Description |
|--------|------|-------------|
| oderId | String | User ID (phone number) - Primary key |
| sentAt | Long | When welcome was sent |
| messageCount | Int | How many messages sent |

## Settings

| Setting | Default | Description |
|---------|---------|-------------|
| isEnabled | false | Master switch |
| sendMultiple | false | Send all messages or just first |
| delayBetweenMessages | 1000ms | Delay between multiple messages |
| onlyNewContacts | true | Only send to unknown contacts |

## Usage

### Enable Welcome Message
1. Go to AutoRespond → Menu → Welcome Message
2. Toggle "Enable Welcome Message" ON
3. Add your welcome message(s)
4. Choose Single or Multiple mode

### Add Messages
1. Tap the + button
2. Enter your message
3. Optionally set delay (for multiple messages)
4. Save

### Reset Sent History
- Tap "Reset Sent" to clear all sent records
- All contacts will receive welcome again

## Integration

Welcome message check happens in `WhatsAppNotificationListener.kt`:
```kotlin
// Check for Welcome Message (first-time contacts)
val welcomeManager = WelcomeMessageManager(this)
val shouldSendWelcome = welcomeManager.shouldSendWelcome(userId)

if (shouldSendWelcome) {
    val welcomeMessages = welcomeManager.getWelcomeMessages()
    // Send messages...
    welcomeManager.markWelcomeSent(userId)
}
```

## Priority
Welcome message is sent BEFORE other auto-replies (keyword, menu, AI, etc.) but doesn't block them. After welcome is sent, other reply systems continue to process the message.
